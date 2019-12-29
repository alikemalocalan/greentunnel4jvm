package com.github.alikemalocalan.tunnel

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object HttpProxyServer extends App with Config {
  val logger = LoggerFactory.getLogger(this.getClass)

  start()

  def start(): Unit =
    new Thread { () =>

      logger.info("HttpProxyServer started on port: {}", port)
      val bossGroup = new NioEventLoopGroup(threadCount)
      val workerGroup: EventLoopGroup = new NioEventLoopGroup(1)

      Try {
        new ServerBootstrap()
          .group(bossGroup, workerGroup)
          .channel(classOf[NioServerSocketChannel])
          .handler(new LoggingHandler(LogLevel.INFO))
          .childHandler(new HttpProxyChannelInitializer())
          .bind(port)
          .sync()
          .channel()
          .closeFuture()
          .sync()
      } match {
        case Success(_) =>
          bossGroup.shutdownGracefully()
          workerGroup.shutdownGracefully()
        case Failure(exception) => logger.error("shit happens", exception);
      }

    }.start()

}
