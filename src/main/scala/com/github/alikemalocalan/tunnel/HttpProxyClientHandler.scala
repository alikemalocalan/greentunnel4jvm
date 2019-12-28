package com.github.alikemalocalan.tunnel

import com.github.alikemalocalan.tunnel.models.HttpRequest
import com.github.alikemalocalan.tunnel.utils.DnsOverHttps
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import org.slf4j.{Logger, LoggerFactory}

class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  var clientChannel: Channel = _
  var remoteChannel: Channel = _
  var header: HttpRequest = _

  override def channelActive(ctx: ChannelHandlerContext): Unit = clientChannel = ctx.channel

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val in = msg.asInstanceOf[ByteBuf]
    if (header != null) {
      HttpServiceUtils.writeToHttps(in, remoteChannel) // just forward
    } else {
      val fullRequest = in.copy
      val chunk = HttpServiceUtils.readMainPart(in)
      if (chunk == null) {
        in.release
      } else {
        header = HttpServiceUtils.fromByteBuf(fullRequest, chunk)
        logger.info(System.currentTimeMillis + " {}", header)
        clientChannel.config.setAutoRead(false) // disable AutoRead until remote connection is ready

        if (header.isHttps) // if https, respond 200 to create tunnel
          clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes))

        val b = new Bootstrap
        b.group(clientChannel.eventLoop) // use the same EventLoop
          .channel(clientChannel.getClass)
          .handler(new HttpProxyRemoteHandler(clientChannel))

        val f = b.connect(DnsOverHttps.lookUp(header.host), header.port)
        remoteChannel = f.channel

        f.addListener { future: ChannelFuture =>
          if (future.isSuccess) {
            clientChannel.config().setAutoRead(true); // connection is ready, enable AutoRead
            if (!header.isHttps)
              remoteChannel.writeAndFlush(Unpooled.wrappedBuffer(header.toString().getBytes()));
            else HttpServiceUtils.writeToHttps(in, remoteChannel)
          } else {
            in.release()
            clientChannel.close()
          }
        }
      }
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = flushAndClose(remoteChannel)

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error(" shit happens", cause)
    flushAndClose(clientChannel)
  }

  def flushAndClose(ch: Channel): Unit =
    if (ch != null && ch.isActive) ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
}
