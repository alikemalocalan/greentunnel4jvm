package com.github.alikemalocalan.tunnel

import io.netty.buffer.Unpooled
import io.netty.channel.{Channel, ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import org.slf4j.LoggerFactory

class HttpProxyRemoteHandler(clientChannel: Channel) extends ChannelInboundHandlerAdapter {
  val logger = LoggerFactory.getLogger(this.getClass)

  var remoteChannel: Channel = _

  override def channelActive(ctx: ChannelHandlerContext): Unit = ctx.channel()

  override def channelInactive(ctx: ChannelHandlerContext): Unit = flushAndClose(clientChannel)

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = clientChannel.writeAndFlush(msg)

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error(" shit happens", cause)
    flushAndClose(remoteChannel)
  }

  def flushAndClose(ch: Channel): Unit = if (ch != null && ch.isActive)
    ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)

}