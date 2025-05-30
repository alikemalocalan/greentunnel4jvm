package com.github.alikemalocalan.greentunnel4jvm.handler


import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils.firstHttpsResponse
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils.simple200Response
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.AttributeKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress


class ProxyClientHandler : ChannelInboundHandlerAdapter() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val bootstrap: Bootstrap = Bootstrap()
        .channel(NioSocketChannel::class.java)
        .option(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)

    companion object {
        private val REMOTE_CHANNEL_KEY: AttributeKey<Channel> = AttributeKey.valueOf("remoteChannel")
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(firstHttpsResponse()) // if https,return respond 200
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buf: ByteBuf = msg as ByteBuf
        val remoteChannel: Channel? = ctx.channel().attr(REMOTE_CHANNEL_KEY).get()
        fun deleteRemoteChannel() {
            ctx.channel().attr(REMOTE_CHANNEL_KEY).set(null)
        }

        remoteChannel?.let { // request take second time from the client
            HttpServiceUtils.splitAndWriteByteBuf(buf, remoteChannel)
        } ?: HttpServiceUtils.httpRequestFromByteBuf(buf)
            .ifPresent { request ->  // request take first time from the client
                val remoteAddressOpt = request.toInetSocketAddress()
                if (remoteAddressOpt.isEmpty) {
                    // DNSOverHttps blocked host
                    ctx.writeAndFlush(simple200Response()).addListener(ChannelFutureListener.CLOSE)
                } else
                    if (request.isHttps) {
                        sendRequestToRemoteChannel(ctx, request, remoteAddressOpt.get())
                    } else { //if http,force to https without any remote connection
                        val response = HttpServiceUtils.redirectHttpToHttps(request.host())
                        ctx.writeAndFlush(response)
                        deleteRemoteChannel()
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
        val remoteChannel = remoteFuture.channel()
        if (remoteChannel.isOpen) {
            remoteFuture.addListener { future ->
                if (future.isSuccess) {
                    ctx.channel().config().isAutoRead = true
                } else {
                    logger.error("Connection failed to ${remoteAddress.hostName}:${remoteAddress.port}: ${future.cause()?.message}")
                }
            }
            ctx.channel().attr(REMOTE_CHANNEL_KEY).set(remoteChannel)
            return remoteChannel
        } else {
            logger.error("Remote channel is not open:${request.uri} , ${remoteAddress.hostName}:${remoteAddress.port}")
            ctx.writeAndFlush(simple200Response()).addListener(ChannelFutureListener.CLOSE)
            ctx.channel().attr(REMOTE_CHANNEL_KEY).set(null)
            remoteChannel.closeFuture().addListener(ChannelFutureListener.CLOSE)
            return ctx.channel()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val remoteChannel = ctx.channel().attr(REMOTE_CHANNEL_KEY).get()
        val remoteAddress = remoteChannel?.remoteAddress()?.toString() ?: "unknown"

        logger.error("Client Connection error: $remoteAddress, error: ${cause.message}")
        remoteChannel?.close()?.addListener(ChannelFutureListener.CLOSE)
        ctx.channel()?.attr(REMOTE_CHANNEL_KEY)?.set(null)
        ctx.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        val remoteChannel = ctx?.channel()?.attr(REMOTE_CHANNEL_KEY)?.get()
        remoteChannel?.close()
        ctx?.channel()?.attr(REMOTE_CHANNEL_KEY)?.set(null)
    }

}