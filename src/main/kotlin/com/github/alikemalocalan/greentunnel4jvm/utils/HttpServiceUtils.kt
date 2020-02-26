package com.github.alikemalocalan.greentunnel4jvm.utils

import arrow.core.*
import arrow.core.extensions.fx
import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.util.concurrent.GenericFutureListener
import java.net.URI
import java.nio.charset.Charset

object HttpServiceUtils {
    private const val clientHelloMTU: Int = 64

    private fun readMainPart(buf: ByteBuf): String {
        val lineBuf = StringBuffer()

        fun readByteBuf(isReadable: Boolean, result: Option<String>): Option<String> {
            return if (isReadable) {
                val b: Byte = buf.readByte()
                lineBuf.append(b.toChar())
                val len: Int = lineBuf.length
                return if (len >= 2 && lineBuf.substring(len - 2).equals("\r\n")) {
                    readByteBuf(isReadable = false, result = Some(lineBuf.substring(0, len - 2)))
                } else readByteBuf(buf.isReadable, None)
            } else result
        }

        return readByteBuf(buf.isReadable, None)
            .getOrElse {
                buf.release()
                ""
            }

    }

    fun fromByteBuf(buf: ByteBuf): Option<HttpRequest> {
        val chunky = readMainPart(buf)
        return if (chunky.isEmpty()) None
        else Some(parseByteBuf(chunky, buf))
    }

    private fun parseByteBuf(chunky: String, buf: ByteBuf): HttpRequest {
        val firstLine = chunky.split(" ")
        val method = firstLine[0]
        val host = firstLine[1].toLowerCase()
        val protocolVersion = firstLine[2]
        return if (method.toUpperCase().startsWith("CONNECT")) { // Https request
            val uri = if (host.startsWith("https://")) URI(host) else URI("https://$host")
            val port = Either.fx<Exception, Int> { uri.port }.toOption().filter { n -> n != -1 }.getOrElse { 443 }
            HttpRequest(method, uri, port = port, protocolVersion = protocolVersion, isHttps = true)
        } else { // Http request
            val uri = if (host.startsWith("http://")) URI(host) else URI("http://$host")
            val port = Either.fx<Exception, Int> { uri.port }.toOption().filter { n -> n != -1 }.getOrElse { 80 }

            val reqAsString: String = buf.asReadOnly().toString(Charset.defaultCharset())
            val mainPart = reqAsString.split("\r\n\r\n") // until payload
            val headerLines = mainPart.first().split("\r\n") // for headers

            val payLoad = if (mainPart.size == 2) mainPart[1] else ""

            val headers: List<Pair<String, String>> = headerLines
                .map { h ->
                    val arr = h.split(":")
                    (arr[0] to arr[1])
                }.toList().distinct()
                .filterNot { k -> k.first == "Proxy-Connection" || k.first == "Via" }

            return HttpRequest(
                method,
                uri,
                protocolVersion,
                port,
                isHttps = false,
                headers = Some(headers),
                payload = Some(payLoad)
            )
        }

    }

    fun writeToHttps(buf: ByteBuf, remoteChannel: Channel): Unit {
        if (buf.isReadable) {
            val bufSize: Int = if (buf.readableBytes() > clientHelloMTU) clientHelloMTU else buf.readableBytes()
            remoteChannel.writeAndFlush(buf.readSlice(bufSize).retain())
                .addListener(GenericFutureListener { future: ChannelFuture ->
                    if (future.isSuccess) remoteChannel.read()
                    else future.channel().close()
                })
            writeToHttps(buf, remoteChannel)
        }
    }


    fun closeOnFlush(ch: Channel) {
        if (ch.isActive) ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    }

}