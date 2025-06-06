package com.github.alikemalocalan.greentunnel4jvm

import com.github.alikemalocalan.greentunnel4jvm.handler.ProxyClientHandler
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class ProxyServer {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val workerGroup = MultiThreadIoEventLoopGroup(10,NioIoHandler.newFactory())
    private val bossGroup: EventLoopGroup = MultiThreadIoEventLoopGroup(10,NioIoHandler.newFactory())

    private val bootstrap: ServerBootstrap =
        ServerBootstrap().group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 1024).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(32 * 1024, 64 * 1024))
            .childOption(ChannelOption.SO_KEEPALIVE, true)


    fun createNettyServer(port: Int = 8080) {
        logger.info("HttpProxyServer started on :${port}")
        try {
            bootstrap.childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(
                        ProxyClientHandler()
                    )
                }
            }).bind(port).sync().channel().closeFuture().sync()
        } finally {
            stop()
        }
    }

    fun stop(): Boolean {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        return true
    }
}

fun main() {
    val port: Any? = System.getProperties()["proxy.port"]

    fun getPort() = port?.toString()?.toInt() ?: HttpServiceUtils.defaultPort

    val server = ProxyServer()

    server.createNettyServer(port = getPort())
}