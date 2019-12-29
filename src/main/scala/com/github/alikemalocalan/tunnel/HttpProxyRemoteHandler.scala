package com.github.alikemalocalan.tunnel

import io.netty.buffer.Unpooled
import io.netty.channel.{Channel, ChannelFuture, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import org.slf4j.LoggerFactory

class HttpProxyRemoteHandler(clientChannel: Channel) extends ChannelInboundHandlerAdapter {
  val logger = LoggerFactory.getLogger(this.getClass)

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit =
    clientChannel.writeAndFlush(msg)
      .addListener { future: ChannelFuture =>
        if (future.isSuccess) ctx.channel().read()
        else future.channel().close()
      }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error("Shit happen at Server Connection", cause)
    ctx.close()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    ctx.read();
    ctx.write(Unpooled.EMPTY_BUFFER);
  }
}