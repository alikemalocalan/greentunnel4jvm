package com.github.alikemalocalan.greentunnel4jvm.handler


import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils.firstHttpsResponse
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils.simple200Response
import com.github.alikemalocalan.greentunnel4jvm.utils.TlsUtils
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.AttributeKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class ProxyClientHandler(private val isAggressiveMode: Boolean = false) : ChannelInboundHandlerAdapter() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private val REMOTE_CHANNEL_KEY = AttributeKey.valueOf<Channel>("remoteChannel")
        private val TARGET_HOST_KEY = AttributeKey.valueOf<String>("targetHost")

        @Volatile
        private var sharedBootstrap: Bootstrap? = null

        private fun getBootstrap(): Bootstrap {
            return sharedBootstrap ?: synchronized(this) {
                sharedBootstrap ?: Bootstrap()
                    .channel(NioSocketChannel::class.java)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .also { sharedBootstrap = it }
            }
        }
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
            TlsUtils.splitAtSni(buf, remoteChannel, isAggressiveMode)
        } ?: HttpServiceUtils.httpRequestFromByteBuf(buf)
            .ifPresent { request ->  // request take first time from the client
                ctx.channel().attr(TARGET_HOST_KEY).set(request.host())
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
        ctx.channel().config().isAutoRead = false

        val remoteFuture = getBootstrap().clone()
            .group(ctx.channel().eventLoop())
            .handler(ProxyRemoteHandler(ctx, request))
            .connect(remoteAddress)

        remoteFuture.addListener(ChannelFutureListener { future ->
            if (future.isSuccess) {
                val remoteChannel = future.channel()
                ctx.channel().attr(REMOTE_CHANNEL_KEY).set(remoteChannel)
                ctx.channel().config().isAutoRead = true
                logger.debug("Successfully connected to remote: {} ({})", request.host(), remoteAddress)
            } else {
                ctx.channel().config().isAutoRead = true
                logger.error("Connection failed to ${request.host()} (${remoteAddress.hostName}:${remoteAddress.port}): ${future.cause()?.message}")
                ctx.close()
            }
        })
        return remoteFuture.channel()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val remoteChannel = ctx.channel().attr(REMOTE_CHANNEL_KEY).get()
        val targetHost = ctx.channel().attr(TARGET_HOST_KEY).get()
        val remoteAddress = remoteChannel?.remoteAddress()?.toString()

        val hostInfo = when {
            targetHost != null && remoteAddress != null -> "$targetHost ($remoteAddress)"
            targetHost != null -> targetHost
            remoteAddress != null -> remoteAddress
            else -> "unknown"
        }

        if (isExpectedDisconnect(cause)) {
            logger.debug("Client connection closed: $hostInfo, reason: ${cause.message}")
        } else {
            logger.error("Client Connection error: $hostInfo, error: ${cause.message}")
        }

        remoteChannel?.close()?.addListener(ChannelFutureListener.CLOSE)
        ctx.channel()?.attr(REMOTE_CHANNEL_KEY)?.set(null)
        ctx.close()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val remoteChannel = ctx.channel().attr(REMOTE_CHANNEL_KEY).get()
        remoteChannel?.let {
            if (it.isOpen) {
                it.close()
            }
            ctx.channel().attr(REMOTE_CHANNEL_KEY).set(null)
        }
        ctx.fireChannelInactive()
    }

    private fun isExpectedDisconnect(cause: Throwable?): Boolean {
        if (cause == null) return false
        if (cause is java.nio.channels.ClosedChannelException) return true
        if (cause is java.net.SocketException) {
            val message = cause.message?.lowercase() ?: ""
            return message.contains("connection reset") || message.contains("broken pipe")
        }
        return false
    }

}