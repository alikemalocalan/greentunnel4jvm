package com.github.alikemalocalan.greentunnel4jvm

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.internal.logging.InternalLoggerFactory
import java.net.InetSocketAddress
import java.util.logging.Level
import java.util.logging.Logger


object HttpProxyServer {
    val logger = InternalLoggerFactory.getInstance(this::class.java)
    val loggerFactory = LoggingHandler(LogLevel.WARN)

    val probs = System.getProperties()

    @JvmStatic
    fun newProxyService(socket: InetSocketAddress, threadCount: Int = 2): ChannelFuture {
        logger.debug("HttpProxyServer started on : ${socket.address}:${socket.port}")
        val bossGroup = NioEventLoopGroup(threadCount)
        val workerGroup = NioEventLoopGroup(threadCount)

        return ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .handler(loggerFactory)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(
                        loggerFactory,
                        HttpProxyClientHandler()
                    )
                }
            })
            .bind(socket)
            .sync()
            .channel()
            .closeFuture()
            .sync()
    }

    @JvmStatic
    fun newProxyService(
        address: String = "127.0.0.1",
        port: Int = 8080,
        threadCount: Int = 10
    ): ChannelFuture =
        newProxyService(InetSocketAddress(address, port), threadCount)


    @JvmStatic
    fun main(args: Array<String>) {
        val port = probs["proxy.port"]

        if (port != null) {
            logger.error("Port :$port")
            newProxyService(port = port.toString().toInt())
        } else newProxyService()
        Logger.getLogger("io.netty").level = Level.OFF
    }


    fun stop(server: ChannelFuture): Boolean = server.cancel(false)

}