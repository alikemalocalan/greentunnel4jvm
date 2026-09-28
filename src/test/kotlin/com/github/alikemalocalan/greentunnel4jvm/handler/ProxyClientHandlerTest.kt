package com.github.alikemalocalan.greentunnel4jvm.handler

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class ProxyClientHandlerTest {

    @Test
    fun `channelActive should not leak proxy banner before receiving request`() {
        val channel = EmbeddedChannel(ProxyClientHandler())

        val initialResponse: ByteBuf? = channel.readOutbound()
        assertNull(initialResponse, "Initial connection must not leak any proxy banner before receiving a request")
        channel.finishAndReleaseAll()
    }

    @Test
    fun `channelRead should return Nginx 404 response on scanner probe or unrecognized payload`() {
        val channel = EmbeddedChannel(ProxyClientHandler())

        // Send active probe / port scanner probe
        val scannerProbe = Unpooled.wrappedBuffer("GET /non-existent-path HTTP/1.0\r\n\r\n".toByteArray())
        channel.writeInbound(scannerProbe)

        val probeResponse: ByteBuf? = channel.readOutbound()
        assertTrue(probeResponse != null, "Scanner probe should receive an outbound response")
        val probeStr = probeResponse!!.toString(StandardCharsets.UTF_8)
        probeResponse.release()

        assertTrue(probeStr.contains("HTTP/1.1 404 Not Found"))
        assertTrue(probeStr.contains("Server: nginx/1.24.0"))
        assertTrue(probeStr.contains("<center>nginx/1.24.0</center>"))

        assertFalse(channel.isOpen, "Channel should be closed after serving 404 to probe")
        channel.finishAndReleaseAll()
    }

    @Test
    fun `channelRead should return Nginx 404 on direct web server probe to root or loopback`() {
        val channel = EmbeddedChannel(ProxyClientHandler())

        val rootProbe = Unpooled.wrappedBuffer("GET / HTTP/1.1\r\nHost: 127.0.0.1:8080\r\nUser-Agent: Mozilla/5.0\r\n\r\n".toByteArray())
        channel.writeInbound(rootProbe)

        val probeResponse: ByteBuf? = channel.readOutbound()
        assertTrue(probeResponse != null)
        val probeStr = probeResponse!!.toString(StandardCharsets.UTF_8)
        probeResponse.release()

        assertTrue(probeStr.contains("HTTP/1.1 404 Not Found"))
        assertTrue(probeStr.contains("Server: nginx/1.24.0"))
        assertFalse(channel.isOpen)
        channel.finishAndReleaseAll()
    }

    @Test
    fun `channelRead should return Nginx 404 on loopback CONNECT probe`() {
        val channel = EmbeddedChannel(ProxyClientHandler())

        val loopbackProbe = Unpooled.wrappedBuffer("CONNECT 127.0.0.1:8080 HTTP/1.1\r\nHost: 127.0.0.1:8080\r\n\r\n".toByteArray())
        channel.writeInbound(loopbackProbe)

        val probeResponse: ByteBuf? = channel.readOutbound()
        assertTrue(probeResponse != null)
        val probeStr = probeResponse!!.toString(StandardCharsets.UTF_8)
        probeResponse.release()

        assertTrue(probeStr.contains("HTTP/1.1 404 Not Found"))
        assertTrue(probeStr.contains("Server: nginx/1.24.0"))
        assertFalse(channel.isOpen)
        channel.finishAndReleaseAll()
    }

    @Test
    fun `channelRead should return 301 redirect on valid external HTTP request`() {
        val channel = EmbeddedChannel(ProxyClientHandler())

        val httpReq = Unpooled.wrappedBuffer("GET http://example.com/test HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray())
        channel.writeInbound(httpReq)

        val response: ByteBuf? = channel.readOutbound()
        assertTrue(response != null)
        val respStr = response!!.toString(StandardCharsets.UTF_8)
        response.release()

        assertTrue(respStr.contains("301 Moved Permanently"))
        assertTrue(respStr.contains("Location: https://example.com"))
        channel.finishAndReleaseAll()
    }

    @Test
    fun `channelRead should stream subsequent application data directly after first handshake`() {
        val clientChannel = EmbeddedChannel(ProxyClientHandler())
        val mockRemoteChannel = EmbeddedChannel()

        val remoteAttr = io.netty.util.AttributeKey.valueOf<io.netty.channel.Channel>("remoteChannel")
        clientChannel.attr(remoteAttr).set(mockRemoteChannel)

        // 1st packet: Handshake / ClientHello
        val firstPacket = Unpooled.wrappedBuffer(byteArrayOf(0x16, 0x03, 0x01, 0x00, 0x05, 0x01, 0x00, 0x00, 0x01, 0x00))
        clientChannel.writeInbound(firstPacket)

        // Clear outbound of mockRemoteChannel
        var out: ByteBuf? = mockRemoteChannel.readOutbound()
        while (out != null) {
            out.release()
            out = mockRemoteChannel.readOutbound()
        }

        // 2nd packet: Subsequent application data (500 bytes payload)
        val testPayload = ByteArray(500) { (it % 100).toByte() }
        clientChannel.writeInbound(Unpooled.wrappedBuffer(testPayload))

        // Read from mockRemoteChannel — it should be received as a single unfragmented buffer
        val appData: ByteBuf? = mockRemoteChannel.readOutbound()
        assertTrue(appData != null, "Application data must be forwarded to remote channel")
        val receivedBytes = ByteArray(appData!!.readableBytes())
        appData.readBytes(receivedBytes)
        appData.release()

        org.junit.jupiter.api.Assertions.assertArrayEquals(testPayload, receivedBytes, "Subsequent application data must be streamed directly without fragmentation")
        org.junit.jupiter.api.Assertions.assertNull(mockRemoteChannel.readOutbound(), "No additional fragments should be generated for application data")

        clientChannel.finishAndReleaseAll()
        mockRemoteChannel.finishAndReleaseAll()
    }
}
