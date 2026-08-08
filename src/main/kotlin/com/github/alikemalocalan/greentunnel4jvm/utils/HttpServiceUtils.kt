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
    private const val MTU_MIN: Int = 40
    private const val MTU_MAX: Int = 160

    private fun randomMTU(): Int = Random.nextInt(MTU_MIN, MTU_MAX + 1)

    private val PROXY_HEADERS_TO_REMOVE = setOf(
        "Client-IP",
        "X-Forwarded-For",
        "X-Forwarded-Host",
        "X-Forwarded-Proto",
        "X-Real-IP",
        "Forwarded",
        "Via",
        "Proxy-Authorization",
        "Proxy-Connection"
    )

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
                    payload = Optional.empty()
                )
            }

            method.equals("CONNECT", ignoreCase = true) -> {
                // Handle HTTPS request
                val uri: URI = if (host.startsWith("https://")) URI(host) else URI("https://$host")
                HttpRequest(method, uri, port = 443, protocolVersion = protocolVersion, isHttps = true)
            }

            else -> {
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
        val mainPart = reqAsString.split("\r\n\r\n")
        val headerLines = mainPart.first().split("\r\n").drop(1)

        return headerLines
            .asSequence()
            .map { h ->
                val arr = h.split(":", limit = 2)
                if (arr.size == 2) arr[0] to arr[1] else "" to ""
            }
            .distinct()
            .filterNot { h -> h.first in PROXY_HEADERS_TO_REMOVE }
            .map(this::addKeepAliveHeaders)
            .map(this::mixHostLetterCase)
            .map(this::randomizeHeaderValues)
            .toList()
    }

    private fun randomizeHeaderValues(header: Pair<String, String>): Pair<String, String> {
        return when (header.first) {
            "User-Agent" -> header.copy(second = randomizeUserAgent(header.second))
            "Accept-Encoding" -> header.copy(second = "gzip, deflate")
            else -> header
        }
    }

    private val USER_AGENT_POOL = listOf(
        // Windows - Chrome
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        // macOS - Chrome
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        // Windows - Firefox
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
        // macOS - Safari
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7_2) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Safari/605.1.15",
        // Windows - Edge
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
        // Linux - Chrome
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        // Linux - Firefox
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0"
    )

    private fun randomizeUserAgent(userAgent: String): String {
        return USER_AGENT_POOL[Random.nextInt(USER_AGENT_POOL.size)]
    }

    private fun extractPayload(reqAsString: String): String {
        val mainPart = reqAsString.split("\r\n\r\n")
        return if (mainPart.size == 2) mainPart[1] else ""
    }

    tailrec fun splitAndWriteByteBuf(buf: ByteBuf, remoteChannel: Channel) {
        if (buf.isReadable) {
            val mtu = randomMTU()
            val bufSize: Int = if (buf.readableBytes() > mtu) mtu else buf.readableBytes()
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