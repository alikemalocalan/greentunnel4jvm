package com.github.alikemalocalan.greentunnel4jvm

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.internal.logging.InternalLoggerFactory


class HttpProxyRemoteHandler(val clientChannel: Channel) : ChannelInboundHandlerAdapter() {
    val logger = InternalLoggerFactory.getInstance(this::class.java)

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        flushAndClose(clientChannel)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        clientChannel.writeAndFlush(msg)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.read()
        ctx.write(Unpooled.EMPTY_BUFFER)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Shit happen at Server Connection", cause)
        flushAndClose(clientChannel)
        ctx.close()
    }

    private fun flushAndClose(ch: Channel?) {
        if (ch != null && ch.isActive) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }


}