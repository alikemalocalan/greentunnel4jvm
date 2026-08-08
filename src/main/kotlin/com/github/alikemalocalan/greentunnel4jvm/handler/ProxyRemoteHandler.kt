package com.github.alikemalocalan.greentunnel4jvm.handler

import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.nio.channels.ClosedChannelException


class ProxyRemoteHandler(private val clientChannel: ChannelHandlerContext, private val request: HttpRequest) :
    ChannelInboundHandlerAdapter() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (!clientChannel.channel().isActive) {
            ctx.close()
            return
        }

        val future = clientChannel.writeAndFlush(msg)
        future.addListener { f ->
            if (!f.isSuccess) {
                val cause = f.cause()
                if (isExpectedDisconnect(cause)) {
                    logger.debug("Client channel closed while relaying remote response: ${cause?.message}")
                } else {
                    logger.error("Failed to write response to client: ${cause?.message}")
                }
                closeBothSides(ctx)
            }
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        HttpServiceUtils.splitAndWriteByteBuf(request.toByteBuf(), ctx.channel())
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val remoteAddress = request.toInetSocketAddress().orElse(null)

        if (isExpectedDisconnect(cause)) {
            logger.debug("Remote connection closed: ${request.host()} ($remoteAddress), reason: ${cause.message}")
        } else {
            logger.error("Remote connection error: ${request.host()} ($remoteAddress)", cause)
        }

        closeBothSides(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (clientChannel.channel().isActive) {
            clientChannel.close()
        }
        ctx.fireChannelInactive()
    }

    private fun closeBothSides(ctx: ChannelHandlerContext) {
        if (ctx.channel().isOpen) {
            ctx.close()
        }
        if (clientChannel.channel().isOpen) {
            clientChannel.close()
        }
    }

    private fun isExpectedDisconnect(cause: Throwable?): Boolean {
        if (cause == null) {
            return false
        }

        if (cause is ClosedChannelException) {
            return true
        }

        if (cause is SocketException) {
            val message = cause.message?.lowercase() ?: ""
            return message.contains("connection reset") || message.contains("broken pipe")
        }

        return false
    }

}