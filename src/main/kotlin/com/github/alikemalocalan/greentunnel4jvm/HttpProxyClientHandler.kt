package com.github.alikemalocalan.greentunnel4jvm


import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import com.github.alikemalocalan.greentunnel4jvm.utils.DNSOverHttps
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOption
import io.netty.util.internal.logging.InternalLoggerFactory
import okio.internal.commonAsUtf8ToByteArray


class HttpProxyClientHandler : ChannelInboundHandlerAdapter() {
    private val logger = InternalLoggerFactory.getInstance(this::class.java)
    private var remoteChannel: Option<Channel> = None

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val clientChannel = ctx.channel()
        val buf: ByteBuf = msg as ByteBuf

        if (remoteChannel.isEmpty()) { // it's first request
            if (buf.readableBytes() < 0) buf.release()
            else
                HttpServiceUtils.fromByteBuf(buf).map { request ->
                    logger.error("${System.currentTimeMillis()}| $request")
                    clientChannel.config().isAutoRead = false // disable AutoRead until remote connection is ready
                    remoteChannel = Some(sendRequestWithRemoteChannel(ctx, clientChannel, request, buf))
                }
        } else // just forward and taking Response
            remoteChannel.map { r ->
                HttpServiceUtils.writeToHttps(buf, r)
                buf.release()
            }

    }

    private fun sendRequestWithRemoteChannel(
        ctx: ChannelHandlerContext,
        clientChannel: Channel,
        request: HttpRequest,
        postBuf: ByteBuf
    ): Channel {

        if (request.isHttps) // if https,return respond 200
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".commonAsUtf8ToByteArray()))

        val remoteFuture = Bootstrap()
            .group(clientChannel.eventLoop()) // use the same EventLoop
            .channel(ctx.channel()::class.java)
            .option(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.AUTO_READ, true) // disable AutoRead until remote connection is ready
            .handler(HttpProxyRemoteHandler(ctx.channel()))
            .connect(DNSOverHttps.lookUp(request.host()), request.port)

        remoteFuture.addListener { future ->
            if (future.isSuccess) {
                clientChannel.config().isAutoRead = true // connection is ready, enable AutoRead
                if (request.isHttps)
                    HttpServiceUtils.writeToHttps(request.toByteBuf(), remoteFuture.channel())
                else remoteFuture.channel().writeAndFlush(request.toByteBuf())
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

}