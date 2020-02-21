package com.github.alikemal.greentunnelmobil.tunnel

import com.github.alikemal.greentunnelmobil.tunnel.models.HttpRequest
import com.github.alikemal.greentunnelmobil.tunnel.utils.DNSOverHttps
import com.github.alikemal.greentunnelmobil.tunnel.utils.HttpServiceUtils
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.util.internal.logging.InternalLoggerFactory


class HttpProxyClientHandler : ChannelInboundHandlerAdapter() {
    val logger = InternalLoggerFactory.getInstance(this::class.java)
    var remoteChannel: Channel? = null
    var isFirstRequest: Boolean = true

    @ExperimentalStdlibApi
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val clientChannel = ctx.channel()
        val buf: ByteBuf = msg as ByteBuf

        if (!isFirstRequest) { // just forward and taking Response
            HttpServiceUtils.writeToHttps(buf, remoteChannel!!)
            buf.release()
        } else {
            if (buf.readableBytes() < 0) buf.release()
            else
                HttpServiceUtils.fromByteBuf(buf).map { request ->
                    logger.info("${System.currentTimeMillis()}| $request")
                    clientChannel.config().isAutoRead = false // disable AutoRead until remote connection is ready
                    remoteChannel = sendRequestWithRemoteChannel(ctx, clientChannel, request, buf)
                    isFirstRequest = false
                }
        }

    }

    @ExperimentalStdlibApi
    private fun sendRequestWithRemoteChannel(
        ctx: ChannelHandlerContext,
        clientChannel: Channel,
        request: HttpRequest,
        postBuf: ByteBuf
    ): Channel {
        // if https, respond 200 to create com.github.alikemalocalan.tunnel
        if (request.isHttps) clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".encodeToByteArray()))

        val remoteFuture = Bootstrap()
            .group(clientChannel.eventLoop()) // use the same EventLoop
            .channel(ctx.channel()::class.java)
            .option<Boolean>(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false)
            .option<Boolean>(ChannelOption.TCP_NODELAY, true)
            .option<Boolean>(ChannelOption.SO_KEEPALIVE, true)
            .option<Boolean>(ChannelOption.AUTO_READ, true) // disable AutoRead until remote connection is ready
            .handler(HttpProxyRemoteHandler(ctx.channel()))
            .connect(DNSOverHttps.lookUp(request.host()), request.port)

        remoteFuture.addListener { future ->
            if (future.isSuccess) {
                clientChannel.config().isAutoRead = true // connection is ready, enable AutoRead
                if (!request.isHttps)
                    remoteFuture.channel().writeAndFlush(request.toByteBuf())
                else HttpServiceUtils.writeToHttps(request.toByteBuf(), remoteFuture.channel())
                ctx.channel().read()
            } else {
                postBuf.release()
                ctx.close()
            }
        }
        return remoteFuture.channel()
    }


    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Shit happen at Server Connection", cause)
        ctx.close()
    }

    private fun flushAndClose(ch: Channel?) {
        if (ch != null && ch.isActive) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }


}