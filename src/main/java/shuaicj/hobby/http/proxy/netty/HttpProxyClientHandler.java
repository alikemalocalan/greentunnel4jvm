package shuaicj.hobby.http.proxy.netty;

import com.github.alikemalocalan.tunnel.HttpRequest;
import com.github.alikemalocalan.tunnel.HttpRequestUtils;
import com.github.alikemalocalan.tunnel.utils.DnsOverHttps;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HttpProxyClientHandler extends ChannelInboundHandlerAdapter {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    private Channel clientChannel;
    private Channel remoteChannel;
    private HttpRequest header;
    String chunk = null;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        clientChannel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (header != null) {
            remoteChannel.writeAndFlush(msg); // just forward
            return;
        }

        ByteBuf in = (ByteBuf) msg;
        final ByteBuf fullRequest = in.copy();

        String chunk = HttpRequestUtils.readMainPart(in);
        if (chunk == null) {
            in.release();
            return;
        }
        header = HttpRequestUtils.fromByteBuf(fullRequest, chunk);

        logger.info(System.currentTimeMillis() + " {}", header);
        clientChannel.config().setAutoRead(false); // disable AutoRead until remote connection is ready

        if (header.isHttps()) { // if https, respond 200 to create tunnel
            clientChannel.writeAndFlush(Unpooled.wrappedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes()));
        }

        Bootstrap b = new Bootstrap();
        b.group(clientChannel.eventLoop()) // use the same EventLoop
                .channel(clientChannel.getClass())
                .handler(new HttpProxyRemoteHandler(clientChannel));
        ChannelFuture f = b.connect(DnsOverHttps.lookUp(header.host()), header.port());
        remoteChannel = f.channel();

        f.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                clientChannel.config().setAutoRead(true); // connection is ready, enable AutoRead
                if (!header.isHttps())
                    remoteChannel.writeAndFlush(Unpooled.wrappedBuffer(header.toString().getBytes()));
                else {
                    remoteChannel.writeAndFlush(in);
                }
            } else {
                in.release();
                clientChannel.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        flushAndClose(remoteChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        logger.error(" shit happens", e);
        flushAndClose(clientChannel);
    }

    private void flushAndClose(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
