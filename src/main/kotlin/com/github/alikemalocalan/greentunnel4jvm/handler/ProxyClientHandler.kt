package com.github.alikemalocalan.greentunnel4jvm.handler


import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils.firstHttpsResponse
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOption
import io.netty.channel.socket.nio.NioSocketChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
            remoteChannelOpt.map { remoteChannel ->
                HttpServiceUtils.splitAndWriteByteBuf(buf, remoteChannel)
            }
        } else // request take first time from the client
            HttpServiceUtils.httpRequestFromByteBuf(buf).map { request ->
                if (request.isHttps) {
                    remoteChannelOpt = sendRequestToRemoteChannel(ctx, request)
                } else { //if http,force to https without any remote connection
                    val response = HttpServiceUtils.redirectHttpToHttps(request.host())
                    ctx.writeAndFlush(response)
                    ctx.close()
                }

            }
    }

    private fun sendRequestToRemoteChannel(
        ctx: ChannelHandlerContext,
        request: HttpRequest
    ): Optional<Channel> =
        kotlin.runCatching { request.toInetSocketAddress() }
            .fold(onSuccess = { remoteAddress ->
                val remoteFuture = bootstrap
                    .group(ctx.channel().eventLoop()) // use the same EventLoop
                    .handler(ProxyRemoteHandler(ctx, request))
                    .connect(remoteAddress)

                ctx.channel().config().isAutoRead = false // if remote connection has done, stop reading
                remoteFuture.addListener {
                    ctx.channel().config().isAutoRead = true // connection is ready, enable AutoRead
                }

                return Optional.of(remoteFuture.channel())
            }, onFailure = { return Optional.empty<Channel>() })


    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
        remoteChannelOpt = Optional.empty()
        logger.error("Proxy Client Connection lost !!")
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        if (remoteChannelOpt.isPresent)
            remoteChannelOpt.get().close()
    }

}