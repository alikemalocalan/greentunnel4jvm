package com.github.alikemalocalan.greentunnel4jvm.utils

import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.util.concurrent.GenericFutureListener
import okio.internal.commonAsUtf8ToByteArray
import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*


object HttpServiceUtils {
    private const val clientHelloMTU: Int = 100

    @JvmStatic
    fun fix301MovedResponse(response: String, httpRequest: HttpRequest): ByteBuf {
        val headers = if (response.contains("\r\n"))
            response.split("\r\n")
        else response.split("\n")

        val result: String = (
                if (headers.any { it.startsWith("Connection: close") }) {
                    headers.map { headerLine ->
                        when {
                            headerLine.startsWith("Connection") -> "Connection: keep-alive"
                            headerLine.startsWith("Proxy-Connection") -> "Connection: keep-alive"
                            headerLine.startsWith("Location") -> "Location: https://${httpRequest.host()}${httpRequest.path()}"
                            else -> headerLine
                        }
                    }.filterNot(String::isBlank)
                        .joinToString(separator = "\r\n")
                } else headers.joinToString(separator = "\r\n")
                ) + "\r\n"

        return Unpooled.wrappedBuffer(result.commonAsUtf8ToByteArray())
    }

    @JvmStatic
    fun httpRequestfromByteBuf(buf: ByteBuf): Optional<HttpRequest> {
        return if (buf.isReadable) {
            return Optional.of(parseHttpRequestFromByteBuf(buf.toString(StandardCharsets.UTF_8)))
        } else Optional.empty()
    }

    @JvmStatic
    private fun parseHttpRequestFromByteBuf(reqAsString: String): HttpRequest {
        val firstLine = reqAsString.split("\r\n").first().split(" ")
        val method = firstLine[0]
        val host = firstLine[1].toLowerCase()
        val protocolVersion = firstLine[2]
        return if (method.toUpperCase().startsWith("CONNECT")) { // Https request
            val uri: URI = if (host.startsWith("https://")) URI(host) else URI("https://$host")
            HttpRequest(method, uri, port = 443, protocolVersion = protocolVersion, isHttps = true)
        } else { // Http request
            val uri = if (host.startsWith("http://")) URI(host) else URI("http://$host")
            val port: Int = if (uri.port == -1) 80 else uri.port

            val mainPart = reqAsString.split("\r\n\r\n") // until payload
            val headerLines = mainPart.first().split("\r\n").drop(1) // for headers

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
    fun splitAndWriteByteBuf(buf: ByteBuf, remoteChannel: Channel) {
        if (buf.isReadable) {
            val bufSize: Int = if (buf.readableBytes() > clientHelloMTU) clientHelloMTU else buf.readableBytes()
            remoteChannel.writeAndFlush(buf.readSlice(bufSize).retain())
                .addListener(GenericFutureListener { future: ChannelFuture ->
                    if (future.isSuccess) remoteChannel.read()
                    else future.channel().close()
                })
            splitAndWriteByteBuf(buf, remoteChannel)
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

    @Throws(IllegalArgumentException::class)
    @JvmStatic
    fun availablePort(ipAsString: String): Int? {
        val MIN_PORT_NUMBER = 1100
        val MAX_PORT_NUMBER = 49151

        val port: Int = ipAsString.toInt()
        if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
            throw IllegalArgumentException("Invalid start port: " + port)
        }

        var ss: ServerSocket? = null
        var ds: DatagramSocket? = null
        try {
            ss = ServerSocket(port)
            ss.reuseAddress = true
            ds = DatagramSocket(port)
            ds.reuseAddress = true
            return port
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            ds?.close()
            if (ss != null) {
                try {
                    ss.close()
                } catch (e: IOException) {
                }
            }
        }
        return null
    }

    @JvmStatic
    fun forceHttpToHttps(siteName: String): ByteBuf {
        val method = "HTTP/1.1 301 Moved Permanently"
        val payload = "Redirecting to https://$siteName\n"

        val headerLines: String = listOf(
            "Content-Type: text/plain",
            "Connection: keep-alive",
            "Content-Length: ${payload.length}",
            "Server: greenTunnel",
            "Location: https://$siteName"
        ).joinToString(separator = "\r\n", postfix = "\r\n")

        val responseAsString = String.format(
            "%s\n%s\n%s",
            method,
            headerLines,
            payload,
        )

        return Unpooled.wrappedBuffer(responseAsString.commonAsUtf8ToByteArray())

    }

}