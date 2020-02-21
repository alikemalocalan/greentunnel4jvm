package com.github.alikemalocalan.tunnel

import java.lang

import com.github.alikemalocalan.tunnel.models.HttpRequest
import com.github.alikemalocalan.tunnel.utils.DnsOverHttps
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import org.slf4j.{Logger, LoggerFactory}

class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  var remoteChannel: Channel = _
  var isFirstRequest: Boolean = true

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val clientChannel = ctx.channel()
    val in = msg.asInstanceOf[ByteBuf]

    if (!isFirstRequest) { // just forward and taking Response
      HttpServiceUtils.writeToHttps(in, remoteChannel)
      in.release()
    } else {
      if (in.readableBytes() < 0) in.release
      else {
        HttpServiceUtils.fromByteBuf(in).foreach{ request =>
          logger.info(s"${System.currentTimeMillis}| ${request.toString}")
          clientChannel.config.setAutoRead(false) // disable AutoRead until remote connection is ready
          remoteChannel = sendRequestWithRemoteChannel(ctx, clientChannel, request, in)
          isFirstRequest = false
        }
      }
    }
  }

  private def sendRequestWithRemoteChannel(ctx: ChannelHandlerContext, clientChannel: Channel, request: HttpRequest, postBuf: ByteBuf): Channel = {
    // if https, respond 200 to create tunnel
    if (request.isHttps) clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes))

    val remoteFuture = new Bootstrap()
      .group(clientChannel.eventLoop) // use the same EventLoop
      .channel(ctx.channel().getClass)
      .option[lang.Boolean](ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false)
      .option[lang.Boolean](ChannelOption.TCP_NODELAY, true)
      .option[lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
      .option[lang.Boolean](ChannelOption.AUTO_READ, true) // disable AutoRead until remote connection is ready
      .handler(new HttpProxyRemoteHandler(ctx.channel()))
      .connect(DnsOverHttps.lookUp(request.host), request.port)

    remoteFuture.addListener { future: ChannelFuture =>
      if (future.isSuccess) {
        clientChannel.config().setAutoRead(true) // connection is ready, enable AutoRead
        if (!request.isHttps)
          remoteFuture.channel.writeAndFlush(request.toByteBuf)
        else HttpServiceUtils.writeToHttps(request.toByteBuf, remoteFuture.channel)
        ctx.channel().read()
      } else {
        postBuf.release()
        ctx.close()
      }
    }
    remoteFuture.channel
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error("Shit happen at Client Connection", cause)
    ctx.close()
  }
}
