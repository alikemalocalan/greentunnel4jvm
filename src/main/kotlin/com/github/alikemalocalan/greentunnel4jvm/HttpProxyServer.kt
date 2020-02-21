package com.github.alikemalocalan.greentunnel4jvm

import arrow.core.Either
import arrow.core.extensions.fx
import com.github.alikemal.greentunnelmobil.tunnel.HttpProxyClientHandler
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.internal.logging.InternalLoggerFactory
import java.util.logging.Level
import java.util.logging.Logger


object HttpProxyServer {
    val logger = InternalLoggerFactory.getInstance(this::class.java)
    val loggerFactory = LoggingHandler(LogLevel.ERROR)

    val port = 8080

    val threadCount = 10

    fun newProxyService(): Thread =
        Thread { ->
            logger.info(
                "HttpProxyServer started on port: {}",
                port
            )
            val bossGroup = NioEventLoopGroup(threadCount)
            val workerGroup = NioEventLoopGroup(1)

            Either.fx<Exception, ChannelFuture> {
                ServerBootstrap()
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
                    .bind(port)
                    .sync()
                    .channel()
                    .closeFuture()
                    .sync()

            }.mapLeft { ex ->
                logger.error("shit happens", ex)
            }
        }

    @JvmStatic
    fun main(args: Array<String>) {
        newProxyService().start()

        Logger.getLogger("io.netty").level = Level.OFF
    }

}