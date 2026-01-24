package com.github.alikemalocalan.greentunnel4jvm.utils

import com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.util.CharsetUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.random.Random


object HttpServiceUtils {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    const val defaultPort: Int = 8080
    private const val clientHelloMTU: Int = 100

    @JvmStatic
    fun firstHttpsResponse(): ByteBuf =
        Unpooled.copiedBuffer("HTTP/2 200 Connection Established\r\n\r\n", CharsetUtil.UTF_8)

    @JvmStatic
    fun simple200Response(): ByteBuf =
        Unpooled.copiedBuffer("HTTP/2 200 OK\r\ncontent-length: 0\r\n\r\n", CharsetUtil.UTF_8)

    @JvmStatic
    fun httpRequestFromByteBuf(buf: ByteBuf): Optional<HttpRequest> {
        return if (buf.isReadable) {
            val request = buf.toString(StandardCharsets.UTF_8)
            buf.release()
            return Optional.of(parseHttpRequestFromByteBuf(request))
        } else Optional.empty()
    }

    @JvmStatic
    private fun parseHttpRequestFromByteBuf(reqAsString: String): HttpRequest {
        val firstLine = reqAsString.split("\r\n").first().split(" ")
        val method = firstLine[0]
        val host = firstLine[1].lowercase()
        val protocolVersion = firstLine[2]

        return when {
            method.equals("HEAD", ignoreCase = true) -> {
                // Handle HEAD request
                val uri = if (host.startsWith("http://")) URI(host) else URI("http://$host")
                val port: Int = if (uri.port == -1) 80 else uri.port

                val headers = extractHeaders(reqAsString)
                HttpRequest(
                    method = method,
                    uri = uri,
                    protocolVersion = protocolVersion,
                    port = port,
                    isHttps = false,
                    headers = Optional.of(headers),
                    payload = Optional.empty() // No body for HEAD requests
                )
            }

            method.equals("CONNECT", ignoreCase = true) -> {
                // Handle HTTPS request
                val uri: URI = if (host.startsWith("https://")) URI(host) else URI("https://$host")
                HttpRequest(method, uri, port = 443, protocolVersion = protocolVersion, isHttps = true)
            }

            else -> {
                // Handle HTTP request
                val uri = if (host.startsWith("http://")) URI(host) else URI("http://$host")
                val port: Int = if (uri.port == -1) 80 else uri.port
                val headers = extractHeaders(reqAsString)
                val payload = extractPayload(reqAsString)

                HttpRequest(
                    method = method,
                    uri = uri,
                    protocolVersion = protocolVersion,
                    port = port,
                    isHttps = false,
                    headers = Optional.of(headers),
                    payload = Optional.of(payload)
                )
            }
        }
    }

    private fun extractHeaders(reqAsString: String): List<Pair<String, String>> {
        val mainPart = reqAsString.split("\r\n\r\n") // Until payload
        val headerLines = mainPart.first().split("\r\n").drop(1) // For headers

        return headerLines
            .asSequence()
            .map { h ->
                val arr = h.split(":")
                if (arr.size == 2) arr[0] to arr[1] else "" to "" // Handle invalid headers
            }
            .distinct()
            .filterNot { h -> h.first == "Client-IP" || h.second == "X-Forwarded-For" }
            .filterNot { k -> k.first == "Via" }
            .map(this::addKeepAliveHeaders)
            .map(this::mixHostLetterCase)
            .map(this::randomizeHeaderValues) // Add more complexity here
            .toList()
    }

    // New function to add more randomization to header values
    private fun randomizeHeaderValues(header: Pair<String, String>): Pair<String, String> {
        return when (header.first) {
            "User-Agent" -> header.copy(second = randomizeUserAgent(header.second)) // Customize User-Agent randomly
            "Accept-Encoding" -> header.copy(second = "gzip, deflate") // Mask encoding to a standard one
            else -> header // For other headers, keep them unchanged
        }
    }

    // Randomize User-Agent to make it appear more natural
    private fun randomizeUserAgent(userAgent: String): String {
        val randomUaParts = listOf(
            "Mozilla/5.0",
            "AppleWebKit/537.36",
            "Chrome/120.0.0.0",
            "Safari/537.36",
            "Edg/120.0.0.0"
        )
        return randomUaParts.joinToString(" ") + " " + userAgent.split(" ").drop(1).joinToString(" ")
    }

    private fun extractPayload(reqAsString: String): String {
        val mainPart = reqAsString.split("\r\n\r\n")
        return if (mainPart.size == 2) mainPart[1] else ""
    }

    tailrec fun splitAndWriteByteBuf(buf: ByteBuf, remoteChannel: Channel) {
        if (buf.isReadable) {
            val bufSize: Int = if (buf.readableBytes() > clientHelloMTU) clientHelloMTU else buf.readableBytes()
            remoteChannel.writeAndFlush(buf.readSlice(bufSize).retain())
            splitAndWriteByteBuf(buf, remoteChannel)
        } else buf.release()
    }

    // mix Host header case (test.com -> tEsT.cOm)
    @JvmStatic
    fun makeUpperRandomChar(str: String): String {
        val parts = str.split(".")
        val modifiedParts = parts.map { part ->
            val charArray = part.toCharArray()
            for (i in charArray.indices) {
                if (Random.nextBoolean()) {
                    charArray[i] = charArray[i].uppercaseChar()
                } else {
                    charArray[i] = charArray[i].lowercaseChar()
                }
            }
            StringBuilder(String(charArray))
        }
        return modifiedParts.joinToString(".")
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

    @JvmStatic
    fun availablePort(portAsString: String): Int {
        val MIN_PORT_NUMBER = 1100
        val MAX_PORT_NUMBER = 49151

        val port: Int = portAsString.toInt()
        if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
            logger.error("Invalid start port: $port")
            return defaultPort
        } else {
            return kotlin.runCatching {
                val ss = ServerSocket(port)
                ss.reuseAddress = true
                val ds = DatagramSocket(port)
                ds.reuseAddress = true

                ds.close()
                ss.close()

                port
            }.onFailure {
                logger.error("Port already in use: $port")
            }.getOrDefault(defaultPort)
        }
    }

    @JvmStatic
    fun redirectHttpToHttps(siteName: String): ByteBuf {
        val method = "HTTP/2 301 Moved Permanently"
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
            payload
        )

        return Unpooled.copiedBuffer(responseAsString, CharsetUtil.UTF_8)
    }

}