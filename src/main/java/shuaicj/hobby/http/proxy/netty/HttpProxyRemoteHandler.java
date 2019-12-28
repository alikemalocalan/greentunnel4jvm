package shuaicj.hobby.http.proxy.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyRemoteHandler extends ChannelInboundHandlerAdapter {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    private Channel clientChannel;
    private Channel remoteChannel;

    public HttpProxyRemoteHandler(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.remoteChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        clientChannel.writeAndFlush(msg); // just forward
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(clientChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        logger.error(" shit happens", e);
        flushAndClose(remoteChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
