package com.github.alikemalocalan.greentunnel4jvm.utils

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class HttpServiceUtilsTest {

    @Test
    fun `stripAltSvcHeaders should remove all case variations of Alt-Svc headers`() {
        val original = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Alt-Svc: h3=\":443\"; ma=86400\r\n" +
                "alt-svc: h3-29=\":443\"\r\n" +
                "ALT-SVC: clear\r\n" +
                "Server: gws\r\n\r\n" +
                "Hello World"

        val cleaned = HttpServiceUtils.stripAltSvcHeaders(original)

        assertFalse(cleaned.contains("Alt-Svc:"), "Cleaned response must not contain Alt-Svc")
        assertFalse(cleaned.contains("alt-svc:"), "Cleaned response must not contain alt-svc")
        assertFalse(cleaned.contains("ALT-SVC:"), "Cleaned response must not contain ALT-SVC")
        assertTrue(cleaned.contains("Content-Type: text/html"))
        assertTrue(cleaned.contains("Server: gws"))
        assertTrue(cleaned.contains("Hello World"))
    }

    @Test
    fun `stripAltSvcHeaders should preserve body content even if body contains Alt-Svc text`() {
        val original = "HTTP/1.1 200 OK\r\n" +
                "Server: test\r\n" +
                "Alt-Svc: h3=\":443\"\r\n\r\n" +
                "Documentation about Alt-Svc: h3=\":443\" header."

        val cleaned = HttpServiceUtils.stripAltSvcHeaders(original)

        assertFalse(cleaned.substringBefore("\r\n\r\n").contains("Alt-Svc:"), "Headers should not contain Alt-Svc")
        assertTrue(cleaned.substringAfter("\r\n\r\n").contains("Documentation about Alt-Svc: h3=\":443\" header."), "Body must be preserved exactly")
    }

    @Test
    fun `stripAltSvcHeaders should return unchanged response when Alt-Svc is not present`() {
        val original = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nServer: nginx\r\n\r\nBody"
        val cleaned = HttpServiceUtils.stripAltSvcHeaders(original)
        assertEquals(original, cleaned)
    }

    @Test
    fun `stripAltSvcFromByteBuf should strip Alt-Svc from HTTP ByteBuf and retain binary body`() {
        val headerPart = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nAlt-Svc: h3=\":443\"\r\n\r\n"
        val binaryBody = byteArrayOf(0x00, 0xFF.toByte(), 0xFE.toByte(), 0x12, 0x34)

        val headerBytes = headerPart.toByteArray(StandardCharsets.US_ASCII)
        val fullBytes = ByteArray(headerBytes.size + binaryBody.size)
        System.arraycopy(headerBytes, 0, fullBytes, 0, headerBytes.size)
        System.arraycopy(binaryBody, 0, fullBytes, headerBytes.size, binaryBody.size)

        val inputBuf = Unpooled.wrappedBuffer(fullBytes)
        val resultBuf = HttpServiceUtils.stripAltSvcFromByteBuf(inputBuf)

        assertNotNull(resultBuf)
        val resultBytes = ByteArray(resultBuf.readableBytes())
        resultBuf.getBytes(resultBuf.readerIndex(), resultBytes)

        val resultStr = String(resultBytes, StandardCharsets.US_ASCII)
        assertFalse(resultStr.contains("Alt-Svc:"), "Result buffer should not contain Alt-Svc header")
        assertTrue(resultStr.startsWith("HTTP/1.1 200 OK"))

        // Verify trailing binary body is intact
        val resultBody = resultBytes.takeLast(binaryBody.size).toByteArray()
        assertArrayEquals(binaryBody, resultBody)
        resultBuf.release()
    }

    @Test
    fun `stripAltSvcFromByteBuf should ignore TLS records`() {
        val tlsRecord = byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05)
        val buf = Unpooled.wrappedBuffer(tlsRecord)

        val result = HttpServiceUtils.stripAltSvcFromByteBuf(buf)
        assertSame(buf, result, "TLS record buffer must be returned unchanged without reallocation")
        buf.release()
    }

    @Test
    fun `nginx404Response should return valid Nginx 404 server response`() {
        val buf = HttpServiceUtils.nginx404Response()
        val text = buf.toString(StandardCharsets.UTF_8)
        buf.release()

        assertTrue(text.startsWith("HTTP/1.1 404 Not Found\r\n"))
        assertTrue(text.contains("Server: nginx/1.24.0\r\n"))
        assertTrue(text.contains("Connection: close\r\n"))
        assertTrue(text.contains("<title>404 Not Found</title>"))
        assertTrue(text.contains("<center>nginx/1.24.0</center>"))
    }

    @Test
    fun `httpRequestFromByteBuf should return empty Optional for scanner probes or malformed data`() {
        val scannerProbe = Unpooled.wrappedBuffer("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray())
        val result1 = HttpServiceUtils.httpRequestFromByteBuf(scannerProbe)
        assertTrue(result1.isEmpty, "Scanner probe should return empty Optional")

        val randomBytes = Unpooled.wrappedBuffer(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44))
        val result2 = HttpServiceUtils.httpRequestFromByteBuf(randomBytes)
        assertTrue(result2.isEmpty, "Random binary garbage should return empty Optional")

        val directGetProbe = Unpooled.wrappedBuffer("GET / HTTP/1.1\r\nHost: 127.0.0.1:8080\r\n\r\n".toByteArray())
        val result3 = HttpServiceUtils.httpRequestFromByteBuf(directGetProbe)
        assertTrue(result3.isEmpty, "Direct web server GET probe should return empty Optional")

        val loopbackConnectProbe = Unpooled.wrappedBuffer("CONNECT 127.0.0.1:8080 HTTP/1.1\r\n\r\n".toByteArray())
        val result4 = HttpServiceUtils.httpRequestFromByteBuf(loopbackConnectProbe)
        assertTrue(result4.isEmpty, "Loopback CONNECT probe should return empty Optional")
    }

    @Test
    fun `httpRequestFromByteBuf should correctly parse valid CONNECT request`() {
        val connectReq = "CONNECT youtube.com:443 HTTP/1.1\r\nHost: youtube.com:443\r\n\r\n"
        val buf = Unpooled.wrappedBuffer(connectReq.toByteArray(StandardCharsets.UTF_8))
        val result = HttpServiceUtils.httpRequestFromByteBuf(buf)

        assertTrue(result.isPresent)
        val req = result.get()
        assertEquals("CONNECT", req.method)
        assertEquals("youtube.com", req.host())
        assertEquals(443, req.port)
        assertTrue(req.isHttps)
    }

    @Test
    fun `httpRequestFromByteBuf should correctly parse CONNECT request with space insertion`() {
        val connectReq = "CONNECT   youtube.com:443   HTTP/1.1\r\nHost: youtube.com:443\r\n\r\n"
        val buf = Unpooled.wrappedBuffer(connectReq.toByteArray(StandardCharsets.UTF_8))
        val result = HttpServiceUtils.httpRequestFromByteBuf(buf)

        assertTrue(result.isPresent, "CONNECT with consecutive spaces must be parsed successfully")
        val req = result.get()
        assertEquals("CONNECT", req.method)
        assertEquals("youtube.com", req.host())
        assertEquals(443, req.port)
        assertTrue(req.isHttps)
    }

    @Test
    fun `formatHostHeaderValue should add trailing dot to domain names but not to IP addresses`() {
        HttpServiceUtils.isTrailingDotEnabled = true

        val domainFormatted = HttpServiceUtils.formatHostHeaderValue("example.com")
        assertTrue(domainFormatted.endsWith("."), "Domain host should end with trailing dot: $domainFormatted")
        assertTrue(domainFormatted.lowercase().startsWith("example.com."))

        val ipFormatted = HttpServiceUtils.formatHostHeaderValue("192.168.1.1")
        assertFalse(ipFormatted.endsWith("."), "IPv4 host must not receive trailing dot: $ipFormatted")

        val ipPortFormatted = HttpServiceUtils.formatHostHeaderValue("127.0.0.1:8080")
        assertFalse(ipPortFormatted.contains(".:"), "IPv4 host with port must not have trailing dot: $ipPortFormatted")

        HttpServiceUtils.isTrailingDotEnabled = false
        val noDotFormatted = HttpServiceUtils.formatHostHeaderValue("example.com")
        assertFalse(noDotFormatted.endsWith("."), "Domain host must not end with dot when disabled: $noDotFormatted")

        HttpServiceUtils.isTrailingDotEnabled = true
    }

    @Test
    fun `HttpRequest toString should respect isSpaceInsertionEnabled`() {
        val uri = java.net.URI("http://example.com/test")
        val req = com.github.alikemalocalan.greentunnel4jvm.models.HttpRequest(
            method = "GET",
            uri = uri,
            protocolVersion = "HTTP/1.1",
            port = 80,
            isHttps = false
        )

        HttpServiceUtils.isSpaceInsertionEnabled = true
        val strWithSpaces = req.toString()
        assertTrue(strWithSpaces.startsWith("GET   /test   HTTP/1.1"), "Expected 3 spaces insertion: $strWithSpaces")

        HttpServiceUtils.isSpaceInsertionEnabled = false
        val strWithoutSpaces = req.toString()
        assertTrue(strWithoutSpaces.startsWith("GET /test HTTP/1.1"), "Expected single space: $strWithoutSpaces")

        HttpServiceUtils.isSpaceInsertionEnabled = true
    }

    @Test
    fun `port rotation, Alt-Svc, trailing dot, and space insertion flags can be toggled`() {
        assertTrue(HttpServiceUtils.isStripAltSvcEnabled)
        assertTrue(HttpServiceUtils.isPortRotateEnabled)
        assertTrue(HttpServiceUtils.isTrailingDotEnabled)
        assertTrue(HttpServiceUtils.isSpaceInsertionEnabled)

        HttpServiceUtils.isStripAltSvcEnabled = false
        HttpServiceUtils.isPortRotateEnabled = false
        HttpServiceUtils.isTrailingDotEnabled = false
        HttpServiceUtils.isSpaceInsertionEnabled = false

        assertFalse(HttpServiceUtils.isStripAltSvcEnabled)
        assertFalse(HttpServiceUtils.isPortRotateEnabled)
        assertFalse(HttpServiceUtils.isTrailingDotEnabled)
        assertFalse(HttpServiceUtils.isSpaceInsertionEnabled)

        // Reset to default
        HttpServiceUtils.isStripAltSvcEnabled = true
        HttpServiceUtils.isPortRotateEnabled = true
        HttpServiceUtils.isTrailingDotEnabled = true
        HttpServiceUtils.isSpaceInsertionEnabled = true
    }
}
