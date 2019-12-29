package com.github.alikemalocalan.tunnel

import com.github.alikemalocalan.tunnel.models.HttpRequest
import com.github.alikemalocalan.tunnel.utils.DnsOverHttps
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import org.slf4j.{Logger, LoggerFactory}

class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  var remoteChannel: Channel = _
  var header: HttpRequest = _

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val clientChannel = ctx.channel()
    val in = msg.asInstanceOf[ByteBuf]
    if (header != null) {
      val msg: ByteBuf = ctx.alloc().buffer(HttpServiceUtils.clientHelloMTU)
      msg.writeBytes(in)
      in.release()

      HttpServiceUtils.writeToHttps(msg, remoteChannel) // just forward
    } else {
      val fullRequest = in.copy
      val chunk = HttpServiceUtils.readMainPart(in)
      if (chunk == null) in.release
      else header = HttpServiceUtils.fromByteBuf(fullRequest, chunk)

      logger.info(s"${System.currentTimeMillis}| ${header.toString}")
      clientChannel.config.setAutoRead(false) // disable AutoRead until remote connection is ready

      if (header.isHttps) // if https, respond 200 to create tunnel
        clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes))

      val remoteFuture = new Bootstrap()
        .group(clientChannel.eventLoop) // use the same EventLoop
        .channel(ctx.channel().getClass)
        .option[java.lang.Boolean](ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, header.isHttps)
        .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
        .option[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
        .option[java.lang.Boolean](ChannelOption.AUTO_READ, false)
        .handler(new HttpProxyRemoteHandler(clientChannel))
        .connect(DnsOverHttps.lookUp(header.host), header.port)

      remoteChannel = remoteFuture.channel

      remoteFuture.addListener { future: ChannelFuture =>
        if (future.isSuccess) {
          clientChannel.config().setAutoRead(true) // connection is ready, enable AutoRead
          if (!header.isHttps)
            remoteChannel.writeAndFlush(Unpooled.wrappedBuffer(header.toString().getBytes()));
          else HttpServiceUtils.writeToHttps(in, remoteChannel)
          ctx.channel().read()
        } else {
          in.release()
          ctx.close()
        }
      }

    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error("Shit happen at Client Connection", cause)
    ctx.close()
  }
}
