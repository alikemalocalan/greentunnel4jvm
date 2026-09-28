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

    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val IP_REGEX = "^[0-9.]+$".toRegex()

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
        "Proxy-Connection",
        "Alt-Svc",
        "alt-svc"
    )

    @JvmStatic
    fun firstHttpsResponse(): ByteBuf =
        Unpooled.copiedBuffer("HTTP/1.1 200 Connection Established\r\n\r\n", CharsetUtil.UTF_8)

    @JvmStatic
    fun simple200Response(): ByteBuf =
        Unpooled.copiedBuffer("HTTP/2 200 OK\r\ncontent-length: 0\r\n\r\n", CharsetUtil.UTF_8)

    @Volatile
    var isPortRotateEnabled: Boolean = true

    @Volatile
    var isTrailingDotEnabled: Boolean = true

    @Volatile
    var isSpaceInsertionEnabled: Boolean = true

    private const val NGINX_404_BODY = "<html>\r\n<head><title>404 Not Found</title></head>\r\n<body>\r\n<center><h1>404 Not Found</h1></center>\r\n<hr><center>nginx/1.24.0</center>\r\n</body>\r\n</html>\r\n"

    @JvmStatic
    fun nginx404Response(): ByteBuf {
        val dateHeader = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC))
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Server: nginx/1.24.0\r\n" +
                "Date: $dateHeader\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${NGINX_404_BODY.length}\r\n" +
                "Connection: close\r\n\r\n" +
                NGINX_404_BODY
        return Unpooled.copiedBuffer(response, CharsetUtil.UTF_8)
    }

    @JvmStatic
    fun httpRequestFromByteBuf(buf: ByteBuf): Optional<HttpRequest> {
        return if (buf.isReadable) {
            val request = buf.toString(StandardCharsets.UTF_8)
            buf.release()
            try {
                parseHttpRequestFromByteBuf(request)
            } catch (e: Exception) {
                logger.debug("Failed to parse HTTP request from buffer: ${e.message}")
                Optional.empty()
            }
        } else Optional.empty()
    }

    @JvmStatic
    private fun parseHttpRequestFromByteBuf(reqAsString: String): Optional<HttpRequest> {
        val lines = reqAsString.split("\r\n")
        if (lines.isEmpty() || lines.first().isBlank()) {
            return Optional.empty()
        }
        val firstLine = lines.first().trim().split(WHITESPACE_REGEX)
        if (firstLine.size < 3) {
            return Optional.empty()
        }
        val method = firstLine[0]
        val target = firstLine[1]
        val protocolVersion = firstLine[2]

        val validMethods = setOf("GET", "POST", "HEAD", "CONNECT", "PUT", "DELETE", "OPTIONS", "TRACE", "PATCH")
        if (!validMethods.contains(method.uppercase())) {
            return Optional.empty()
        }

        val uri: URI = try {
            if (method.equals("CONNECT", ignoreCase = true)) {
                if (target.startsWith("https://", ignoreCase = true)) URI(target) else URI("https://$target")
            } else if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) {
                URI(target)
            } else {
                // Any origin-form request (e.g. GET / or GET /admin) or malformed target is direct web server access
                return Optional.empty()
            }
        } catch (_: Exception) {
            return Optional.empty()
        }

        val hostLower = uri.host?.lowercase()?.trimEnd('.')
        if (hostLower.isNullOrBlank() || hostLower == "localhost" || hostLower == "127.0.0.1" || hostLower == "0.0.0.0" || hostLower == "::1") {
            return Optional.empty()
        }

        val port: Int = if (uri.port != -1) uri.port else if (method.equals("CONNECT", ignoreCase = true)) 443 else 80

        val parsed = when {
            method.equals("HEAD", ignoreCase = true) -> {
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
                HttpRequest(method, uri, port = port, protocolVersion = protocolVersion, isHttps = true)
            }

            else -> {
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
        return Optional.of(parsed)
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

    /**
     * Splits data and introduces an inter-fragment delay after the first segment
     * to trigger DPI reassembly buffer timeouts.
     */
    fun splitAndWriteWithDelay(
        buf: ByteBuf,
        remoteChannel: Channel,
        delayMs: LongRange = 1L..30L
    ) {
        if (buf.isReadable) {
            val mtu = randomMTU()
            val bufSize: Int = if (buf.readableBytes() > mtu) mtu else buf.readableBytes()
            remoteChannel.writeAndFlush(buf.readSlice(bufSize).retain())

            if (buf.isReadable) {
                val delay = Random.nextLong(delayMs.first, delayMs.last + 1)
                fun writeRemaining() {
                    if (remoteChannel.isActive) {
                        splitAndWriteByteBuf(buf, remoteChannel)
                    } else {
                        buf.release()
                    }
                }

                if (remoteChannel is io.netty.channel.embedded.EmbeddedChannel) {
                    writeRemaining()
                } else if (delay > 0 && remoteChannel.eventLoop() != null) {
                    remoteChannel.eventLoop().schedule({
                        writeRemaining()
                    }, delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                } else {
                    writeRemaining()
                }
            }
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
    fun formatHostHeaderValue(hostValue: String): String {
        val cleanHost = hostValue.trim()
        val hostWithoutPort = if (cleanHost.contains(":")) cleanHost.substringBefore(":") else cleanHost
        val portPart = if (cleanHost.contains(":")) ":" + cleanHost.substringAfter(":") else ""

        val isIp = hostWithoutPort.matches(IP_REGEX) || hostWithoutPort.contains(":")
        val hostWithDot = if (isTrailingDotEnabled && !isIp && !hostWithoutPort.endsWith(".")) {
            "$hostWithoutPort.$portPart"
        } else {
            cleanHost
        }
        return makeUpperRandomChar(hostWithDot)
    }

    @JvmStatic
    private fun mixHostLetterCase(header: Pair<String, String>): Pair<String, String> =
        if (header.first.equals("host", true))
            makeUpperRandomChar(header.first) to formatHostHeaderValue(header.second)
        else header

    @JvmStatic
    fun availablePort(portAsString: String): Int {
        val MIN_PORT_NUMBER = 1100
        val MAX_PORT_NUMBER = 49151

        val port: Int = portAsString.toInt()
        if (port !in MIN_PORT_NUMBER..MAX_PORT_NUMBER) {
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

    @Volatile
    var isStripAltSvcEnabled: Boolean = true

    /**
     * Strips Alt-Svc / alt-svc headers from an HTTP response header block
     * to prevent browsers from switching to censored QUIC UDP traffic.
     */
    @JvmStatic
    fun stripAltSvcHeaders(responseAsString: String): String {
        val delimIndex = responseAsString.indexOf("\r\n\r\n")
        val (headersPart, bodyPart, delim) = if (delimIndex != -1) {
            Triple(
                responseAsString.substring(0, delimIndex),
                responseAsString.substring(delimIndex + 4),
                "\r\n\r\n"
            )
        } else {
            val lfIndex = responseAsString.indexOf("\n\n")
            if (lfIndex != -1) {
                Triple(
                    responseAsString.substring(0, lfIndex),
                    responseAsString.substring(lfIndex + 2),
                    "\n\n"
                )
            } else {
                Triple(responseAsString, "", "")
            }
        }

        val lines = headersPart.split("\r\n").flatMap { it.split("\n") }
        val filteredLines = lines.filterNot { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex != -1) {
                val key = line.substring(0, colonIndex).trim()
                key.equals("alt-svc", ignoreCase = true)
            } else false
        }

        val cleanedHeaders = filteredLines.joinToString("\r\n")
        return if (delim.isNotEmpty()) {
            cleanedHeaders + delim + bodyPart
        } else {
            cleanedHeaders
        }
    }

    /**
     * Checks if the buffer starts with an HTTP response line (e.g. "HTTP/1.1", "HTTP/2", etc.).
     */
    @JvmStatic
    fun isHttpResponse(buf: ByteBuf): Boolean {
        if (buf.readableBytes() < 5) return false
        val rIdx = buf.readerIndex()
        return buf.getByte(rIdx) == 'H'.code.toByte() &&
                buf.getByte(rIdx + 1) == 'T'.code.toByte() &&
                buf.getByte(rIdx + 2) == 'T'.code.toByte() &&
                buf.getByte(rIdx + 3) == 'P'.code.toByte() &&
                buf.getByte(rIdx + 4) == '/'.code.toByte()
    }

    /**
     * Strips Alt-Svc headers from an incoming HTTP response ByteBuf while
     * keeping the response body (including binary data) intact.
     */
    @JvmStatic
    fun stripAltSvcFromByteBuf(buf: ByteBuf): ByteBuf {
        if (!isStripAltSvcEnabled || !isHttpResponse(buf)) {
            return buf
        }

        val readable = buf.readableBytes()
        val rIdx = buf.readerIndex()
        val bytes = ByteArray(readable)
        buf.getBytes(rIdx, bytes)

        if (!containsAltSvcIgnoreCase(bytes)) {
            return buf
        }

        var headerEnd = -1
        var delimLen = 0

        for (i in 0 until bytes.size - 1) {
            if (i + 3 < bytes.size &&
                bytes[i] == 0x0D.toByte() && bytes[i + 1] == 0x0A.toByte() &&
                bytes[i + 2] == 0x0D.toByte() && bytes[i + 3] == 0x0A.toByte()
            ) {
                headerEnd = i
                delimLen = 4
                break
            } else if (bytes[i] == 0x0A.toByte() && bytes[i + 1] == 0x0A.toByte()) {
                headerEnd = i
                delimLen = 2
                break
            }
        }

        if (headerEnd == -1) {
            val headerStr = String(bytes, StandardCharsets.US_ASCII)
            val cleaned = stripAltSvcHeaders(headerStr)
            buf.release()
            return Unpooled.copiedBuffer(cleaned, StandardCharsets.US_ASCII)
        }

        val headerBytes = bytes.copyOfRange(0, headerEnd)
        val headerStr = String(headerBytes, StandardCharsets.US_ASCII)
        val cleanedHeaderStr = stripAltSvcHeaders(headerStr)
        val cleanedHeaderBytes = cleanedHeaderStr.toByteArray(StandardCharsets.US_ASCII)

        val bodyStart = headerEnd + delimLen
        val bodyLength = bytes.size - bodyStart

        val resultBuf = Unpooled.buffer(cleanedHeaderBytes.size + delimLen + bodyLength)
        resultBuf.writeBytes(cleanedHeaderBytes)
        if (delimLen == 4) {
            resultBuf.writeBytes(byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A))
        } else {
            resultBuf.writeBytes(byteArrayOf(0x0A, 0x0A))
        }
        if (bodyLength > 0) {
            resultBuf.writeBytes(bytes, bodyStart, bodyLength)
        }

        buf.release()
        return resultBuf
    }

    private fun containsAltSvcIgnoreCase(bytes: ByteArray): Boolean {
        val targetLower = byteArrayOf(
            'a'.code.toByte(), 'l'.code.toByte(), 't'.code.toByte(),
            '-'.code.toByte(), 's'.code.toByte(), 'v'.code.toByte(), 'c'.code.toByte()
        )
        val targetUpper = byteArrayOf(
            'A'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(),
            '-'.code.toByte(), 'S'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte()
        )
        if (bytes.size < targetLower.size) return false

        for (i in 0..bytes.size - targetLower.size) {
            var match = true
            for (j in targetLower.indices) {
                val b = bytes[i + j]
                if (b != targetLower[j] && b != targetUpper[j]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

}