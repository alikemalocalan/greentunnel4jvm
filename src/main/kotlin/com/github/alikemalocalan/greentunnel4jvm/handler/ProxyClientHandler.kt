package com.github.alikemalocalan.greentunnel4jvm.handler


import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils.firstHttpsResponse
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils.simple200Response
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.nio.NioSocketChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*


class ProxyClientHandler : ChannelInboundHandlerAdapter() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private var remoteChannelOpt: Optional<Channel> = Optional.empty()

    private val bootstrap: Bootstrap = Bootstrap()
        .channel(NioSocketChannel::class.java)
        .option(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(firstHttpsResponse()) // if https,return respond 200
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buf: ByteBuf = msg as ByteBuf

        if (remoteChannelOpt.isPresent) { // request take second time from the client
            HttpServiceUtils.splitAndWriteByteBuf(buf, remoteChannelOpt.get())
        } else // request take first time from the client
            HttpServiceUtils.httpRequestFromByteBuf(buf).ifPresent { request ->
                val remoteAddressOpt = request.toInetSocketAddress()
                if (remoteAddressOpt.isEmpty) {
                    // DNSOverHttps blocked host
                    ctx.writeAndFlush(simple200Response()).addListener(ChannelFutureListener.CLOSE)
                } else
                    if (request.isHttps) {
                        remoteChannelOpt = Optional.of(sendRequestToRemoteChannel(ctx, request, remoteAddressOpt.get()))
                    } else { //if http,force to https without any remote connection
                        val response = HttpServiceUtils.redirectHttpToHttps(request.host())
                        ctx.writeAndFlush(response)
                    }
            }
    }

    private fun sendRequestToRemoteChannel(
        ctx: ChannelHandlerContext,
        request: HttpRequest,
        remoteAddress: InetSocketAddress
    ): Channel {
        val remoteFuture = bootstrap
            .group(ctx.channel().eventLoop()) // use the same EventLoop
            .handler(ProxyRemoteHandler(ctx, request))
            .connect(remoteAddress)

        ctx.channel().config().isAutoRead = false // if remote connection has done, stop reading
        remoteFuture.addListener {
            ctx.channel().config().isAutoRead = true // connection is ready, enable AutoRead
        }
        return remoteFuture.channel()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        remoteChannelOpt.ifPresent { remoteChannel ->
            logger.error("Proxy Client Connection lost for: ${remoteChannel.remoteAddress()} , error: ${cause.localizedMessage}")
            remoteChannel.close()
        }
        if (remoteChannelOpt.isEmpty) {
            logger.error("Proxy Client Connection lost: remote channel not present , error: ${cause.localizedMessage}")
        }
        ctx.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        if (remoteChannelOpt.isPresent)
            remoteChannelOpt.get().close()
    }

}