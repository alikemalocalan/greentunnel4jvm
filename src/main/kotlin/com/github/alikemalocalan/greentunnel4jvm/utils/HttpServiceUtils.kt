package com.github.alikemalocalan.greentunnel4jvm.utils

import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.util.concurrent.GenericFutureListener
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*

object HttpServiceUtils {
    private const val clientHelloMTU: Int = 100

    @JvmStatic
    private fun readMainPart(buf: ByteBuf): String {
        val lineBuf = StringBuffer()

        fun readByteBuf(isReadable: Boolean, result: Optional<String>): Optional<String> {
            return if (isReadable) {
                val b: Byte = buf.readByte()
                lineBuf.append(b.toChar())
                val len: Int = lineBuf.length
                return if (len >= 2 && lineBuf.substring(len - 2).equals("\r\n")) {
                    readByteBuf(isReadable = false, result = Optional.of(lineBuf.substring(0, len - 2)))
                } else readByteBuf(buf.isReadable, Optional.empty())
            } else result
        }

        return readByteBuf(buf.isReadable, Optional.empty())
            .orElseGet {
                buf.release()
                ""
            }

    }

    @JvmStatic
    fun fromByteBuf(buf: ByteBuf): Optional<HttpRequest> {
        val chunky = readMainPart(buf)
        return if (chunky.isEmpty()) Optional.empty()
        else Optional.of(parseByteBuf(chunky, buf))
    }

    @JvmStatic
    private fun parseByteBuf(chunky: String, buf: ByteBuf): HttpRequest {
        val firstLine = chunky.split(" ")
        val method = firstLine[0]
        val host = firstLine[1].toLowerCase()
        val protocolVersion = firstLine[2]
        return if (method.toUpperCase().startsWith("CONNECT")) { // Https request
            val uri: URI = if (host.startsWith("https://")) URI(host) else URI("https://$host")
            HttpRequest(method, uri, port = 443, protocolVersion = protocolVersion, isHttps = true)
        } else { // Http request
            val uri = if (host.startsWith("http://")) URI(host) else URI("http://$host")
            val port: Int = if (uri.port == -1) 80 else uri.port

            val reqAsString: String = buf.toString(StandardCharsets.UTF_8)
            val mainPart = reqAsString.split("\r\n\r\n") // until payload
            val headerLines = mainPart.first().split("\r\n") // for headers

            val payLoad = if (mainPart.size == 2) mainPart[1] else ""

            val headers: List<Pair<String, String>> = headerLines
                .asSequence()
                .map { h ->
                    val arr = h.split(":")
                    (arr[0] to arr[1])
                }
                .distinct()
                .filterNot { h -> h.first == "Client-IP" || h.second == "X-Forwarded-For" }
                .filterNot { k -> k.first == "Via" }
                .map(this::addKeepAliveHeaders)
                .map(this::mixHostLetterCase)
                .toList()

            return HttpRequest(
                method,
                uri,
                protocolVersion,
                port,
                isHttps = false,
                headers = Optional.of(headers),
                payload = Optional.of(payLoad)
            )
        }

    }

    @JvmStatic
    fun writeToHttps(buf: ByteBuf, remoteChannel: Channel) {
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

    /*
   mix Host header case (test.com -> tEsT.cOm)
    TODO: maybe it will be improve and more complex
     */
    @JvmStatic
    fun makeUpperRandomChar(str: String): String {
        val char = str.elementAt(Random().nextInt(str.length))
        return str.replace(char, char.toUpperCase())
    }

    @JvmStatic
    private fun addKeepAliveHeaders(header: Pair<String, String>): Pair<String, String> =
        if (header.first == "Proxy-Connection" || header.first == "Via")
            "Connection" to "keep-alive"
        else header

    @JvmStatic
    private fun mixHostLetterCase(header: Pair<String, String>): Pair<String, String> =
        if (header.first.equals("host", true))
            makeUpperRandomChar(header.first) to makeUpperRandomChar(header.second)
        else header

}