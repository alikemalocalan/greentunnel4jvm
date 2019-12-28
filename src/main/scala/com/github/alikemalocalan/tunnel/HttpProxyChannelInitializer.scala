package com.github.alikemalocalan.tunnel

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.logging.{LogLevel, LoggingHandler}

class HttpProxyChannelInitializer extends ChannelInitializer[SocketChannel] {

  override def initChannel(ch: SocketChannel): Unit =
    ch.pipeline().addLast(
      new LoggingHandler(LogLevel.INFO),
      new HttpProxyClientHandler()
    )
}
