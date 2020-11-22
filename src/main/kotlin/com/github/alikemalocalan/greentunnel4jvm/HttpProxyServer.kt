package com.github.alikemalocalan.greentunnel4jvm

import ch.qos.logback.classic.util.ContextInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class HttpProxyServer {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val workerGroup = NioEventLoopGroup(25)

    private val bootstrap: ServerBootstrap = ServerBootstrap()
        .group(workerGroup)
        .channel(NioServerSocketChannel::class.java)
        .option(ChannelOption.SO_BACKLOG, 1024)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)


    fun createNettyServer(port: Int = 8080): ChannelFuture {
        logger.debug("HttpProxyServer started on :${port}")
        return bootstrap.childHandler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                ch.pipeline().addLast(
                    HttpProxyClientHandler()
                )
            }
        })
            .bind(port)
            .sync()
            .channel()
            .closeFuture()
            .sync()
    }

    fun stop(server: ChannelFuture?): Boolean? = server?.cancel(false)
}

fun main(args: Array<String>) {
    System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "src/main/resources/console-log-config.xml")
    val port: Any? = System.getProperties()["proxy.port"]

    fun getPort() = port?.toString()?.toInt() ?: 8080

    val server = HttpProxyServer()

    server.createNettyServer(port = getPort())
}