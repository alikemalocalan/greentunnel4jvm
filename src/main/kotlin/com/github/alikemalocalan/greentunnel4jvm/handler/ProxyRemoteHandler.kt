package com.github.alikemalocalan.greentunnel4jvm.handler

import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.jvm.optionals.getOrDefault


class ProxyRemoteHandler(private val clientChannel: ChannelHandlerContext, private val request: HttpRequest) :
    ChannelInboundHandlerAdapter() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        clientChannel.writeAndFlush(msg)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        HttpServiceUtils.splitAndWriteByteBuf(request.toByteBuf(), ctx.channel())
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error(
            "Remote Connection error: ${request.host()} , ${
                request.toInetSocketAddress().map { it.toString() }.getOrDefault("")
            }", cause.message
        )
        ctx.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        ctx.close()
    }

}