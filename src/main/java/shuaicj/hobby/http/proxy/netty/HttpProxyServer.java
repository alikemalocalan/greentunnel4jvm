package shuaicj.hobby.http.proxy.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyServer {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    private int port = 1080;

    public static void main(String[] args) {
        new HttpProxyServer().start();
    }

    public void start() {
        new Thread(() -> {
            logger.info("HttpProxyServer started on port: {}", port);
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .handler(new LoggingHandler(LogLevel.DEBUG))
                        .childHandler(new HttpProxyChannelInitializer())
                        .bind(port).sync().channel().closeFuture().sync();
            } catch (InterruptedException e) {
                logger.error("shit happens", e);
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }).start();
    }
}
