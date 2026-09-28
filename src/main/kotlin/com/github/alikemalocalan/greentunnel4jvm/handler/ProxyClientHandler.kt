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

class ProxyClientHandler : ChannelInboundHandlerAdapter() {
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
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .also { sharedBootstrap = it }
            }
        }
    }

    private var isClientHelloSent: Boolean = false

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buf: ByteBuf = msg as ByteBuf
        val remoteChannel: Channel? = ctx.channel().attr(REMOTE_CHANNEL_KEY).get()
        fun deleteRemoteChannel() {
            ctx.channel().attr(REMOTE_CHANNEL_KEY).set(null)
            isClientHelloSent = false
        }

        remoteChannel?.let {
            if (!isClientHelloSent) {
                isClientHelloSent = true
                TlsUtils.splitAtSni(buf, remoteChannel)
            } else {
                // Handshake already completed, stream application data directly at full line speed
                remoteChannel.writeAndFlush(buf)
            }
        } ?: run {
            val reqOpt = HttpServiceUtils.httpRequestFromByteBuf(buf)
            if (reqOpt.isPresent) {
                val request = reqOpt.get()
                ctx.channel().attr(TARGET_HOST_KEY).set(request.host())
                if (!request.isHttps) {
                    // if http, force to https without any remote connection
                    val response = HttpServiceUtils.redirectHttpToHttps(request.host())
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                    deleteRemoteChannel()
                } else {
                    val remoteAddressOpt = request.toInetSocketAddress()
                    if (remoteAddressOpt.isEmpty) {
                        // DNSOverHttps blocked host
                        ctx.writeAndFlush(simple200Response()).addListener(ChannelFutureListener.CLOSE)
                    } else {
                        sendRequestToRemoteChannel(ctx, request, remoteAddressOpt.get())
                    }
                }
            } else {
                // Active probe scanner defense: serve realistic Nginx 404 page
                logger.warn("Active probe or unrecognized request detected, serving benign Nginx 404")
                ctx.writeAndFlush(HttpServiceUtils.nginx404Response()).addListener(ChannelFutureListener.CLOSE)
            }
        }
    }

    private fun sendRequestToRemoteChannel(
        ctx: ChannelHandlerContext,
        request: HttpRequest,
        remoteAddress: InetSocketAddress,
        isRetry: Boolean = false
    ): Channel {
        ctx.channel().config().isAutoRead = false

        val bootstrap = getBootstrap().clone()
            .group(ctx.channel().eventLoop())
            .handler(ProxyRemoteHandler(ctx, request))

        val remoteFuture = if (HttpServiceUtils.isPortRotateEnabled) {
            val localAddress = if (remoteAddress.address is java.net.Inet6Address) {
                InetSocketAddress(java.net.Inet6Address.getByAddress(ByteArray(16)), 0)
            } else {
                InetSocketAddress(0)
            }
            try {
                bootstrap.connect(remoteAddress, localAddress)
            } catch (_: Exception) {
                bootstrap.connect(remoteAddress)
            }
        } else {
            bootstrap.connect(remoteAddress)
        }

        remoteFuture.addListener(ChannelFutureListener { future ->
            if (future.isSuccess) {
                val remoteChannel = future.channel()
                ctx.channel().attr(REMOTE_CHANNEL_KEY).set(remoteChannel)
                ctx.writeAndFlush(firstHttpsResponse())
                ctx.channel().config().isAutoRead = true
                logger.debug("Successfully connected to remote: {} ({}) [portRotate={}]", request.host(), remoteAddress, HttpServiceUtils.isPortRotateEnabled)
            } else {
                if (!isRetry && HttpServiceUtils.isPortRotateEnabled && ctx.channel().isActive) {
                    logger.warn("Initial connection failed to {} ({}), rotating TCP source port and retrying...", request.host(), remoteAddress)
                    sendRequestToRemoteChannel(ctx, request, remoteAddress, isRetry = true)
                } else {
                    ctx.channel().config().isAutoRead = true
                    logger.error("Connection failed to ${request.host()} (${remoteAddress.hostName}:${remoteAddress.port}): ${future.cause()?.message}")
                    ctx.close()
                }
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