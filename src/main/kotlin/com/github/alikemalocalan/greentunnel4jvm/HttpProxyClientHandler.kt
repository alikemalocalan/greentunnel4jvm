package com.github.alikemalocalan.greentunnel4jvm


import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.internal.logging.InternalLoggerFactory
import okio.internal.commonAsUtf8ToByteArray
import java.util.*


class HttpProxyClientHandler : ChannelInboundHandlerAdapter() {
    private val logger = InternalLoggerFactory.getInstance(this::class.java)
    private var remoteChannel: Optional<Channel> = Optional.empty()

    private val bootstrap: Bootstrap = Bootstrap()
        .channel(NioSocketChannel::class.java)
        .option(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.AUTO_READ, true) // disable AutoRead until remote connection is ready

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buf: ByteBuf = msg as ByteBuf

        if (remoteChannel.isPresent) { // request take second time from the client
            remoteChannel.map { r ->
                HttpServiceUtils.writeToHttps(buf, r)
                buf.release()
            }
        } else // request take first time from the client
            HttpServiceUtils.httpRequestfromByteBuf(buf).map { request ->
                if (request.isHttps) // if https,return respond 200
                    ctx.channel()
                        .writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".commonAsUtf8ToByteArray()))
                ctx.channel().config().isAutoRead = false // disable AutoRead until remote connection is ready
                remoteChannel = Optional.of(sendRequestWithRemoteChannel(ctx, request))
                buf.release()
            }
    }

    private fun sendRequestWithRemoteChannel(
        ctx: ChannelHandlerContext,
        request: HttpRequest
    ): Channel {

        val remoteFuture = bootstrap
            .group(ctx.channel().eventLoop()) // use the same EventLoop
            .handler(HttpProxyRemoteHandler(ctx.channel()))
            .connect(request.toInetSocketAddress())

        remoteFuture.addListener { future ->
            if (future.isSuccess) {
                ctx.channel().config().isAutoRead = true // connection is ready, enable AutoRead
                HttpServiceUtils.writeToHttps(request.toByteBuf(), remoteFuture.channel())
                ctx.channel().read()
            } else ctx.close()
        }
        return remoteFuture.channel()
    }


    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
        logger.error("Netty Server Connection")
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        flushAndClose(ctx?.channel())
    }

    private fun flushAndClose(ch: Channel?) {
        if (ch != null && ch.isActive) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }

}