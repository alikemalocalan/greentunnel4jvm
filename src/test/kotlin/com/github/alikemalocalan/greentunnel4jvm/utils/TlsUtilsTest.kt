package com.github.alikemalocalan.greentunnel4jvm.utils

import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TlsUtilsTest {

    @Test
    fun `isClientHello should correctly identify TLS ClientHello`() {
        val validClientHello = byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x10, 0x01)
        val buf = Unpooled.wrappedBuffer(validClientHello)
        assertTrue(TlsUtils.isClientHello(buf))

        val nonTls = "GET / HTTP/1.1\r\n".toByteArray()
        val nonTlsBuf = Unpooled.wrappedBuffer(nonTls)
        assertFalse(TlsUtils.isClientHello(nonTlsBuf))
    }

    @Test
    fun `findSniInfo should find SNI hostname in ClientHello binary`() {
        val clientHello = buildSyntheticClientHello("example.com")
        val sniInfo = TlsUtils.findSniInfo(clientHello)

        assertNotNull(sniInfo)
        assertEquals("example.com".length, sniInfo!!.hostnameLength)
        val extractedHostname = String(clientHello, sniInfo.hostnameOffset, sniInfo.hostnameLength, Charsets.US_ASCII)
        assertEquals("example.com", extractedHostname)
    }

    @Test
    fun `findSniInfo should return null when SNI is missing`() {
        val clientHelloNoSni = buildSyntheticClientHello(hostname = null)
        val sniInfo = TlsUtils.findSniInfo(clientHelloNoSni)

        assertNull(sniInfo)
    }

    /**
     * Constructs a valid minimal synthetic TLS ClientHello byte array.
     */
    private fun buildSyntheticClientHello(hostname: String?): ByteArray {
        val extensionsData = mutableListOf<Byte>()

        if (hostname != null) {
            val hostBytes = hostname.toByteArray(Charsets.US_ASCII)
            val sniExtDataLen = 2 + 1 + 2 + hostBytes.size
            val sniExtLen = 4 + sniExtDataLen

            // Extension header: Type=0x0000 (SNI), Length=sniExtDataLen
            extensionsData.add(0x00)
            extensionsData.add(0x00)
            extensionsData.add((sniExtDataLen shr 8).toByte())
            extensionsData.add((sniExtDataLen and 0xFF).toByte())

            // SNI List length
            val sniListLen = 1 + 2 + hostBytes.size
            extensionsData.add((sniListLen shr 8).toByte())
            extensionsData.add((sniListLen and 0xFF).toByte())

            // Name Type = 0x00 (host_name)
            extensionsData.add(0x00)

            // Name length
            extensionsData.add((hostBytes.size shr 8).toByte())
            extensionsData.add((hostBytes.size and 0xFF).toByte())

            // Hostname bytes
            for (b in hostBytes) extensionsData.add(b)
        }

        val extBytes = extensionsData.toByteArray()

        // Handshake payload
        val handshakePayload = mutableListOf<Byte>()
        handshakePayload.add(0x03) // Client Version 3.3 (TLS 1.2)
        handshakePayload.add(0x03)

        // Random 32 bytes
        for (i in 0 until 32) handshakePayload.add(i.toByte())

        // Session ID length = 0
        handshakePayload.add(0x00)

        // Cipher suites length = 2 (1 suite: 0x002F)
        handshakePayload.add(0x00)
        handshakePayload.add(0x02)
        handshakePayload.add(0x00)
        handshakePayload.add(0x2F)

        // Compression methods length = 1 (1 method: 0x00)
        handshakePayload.add(0x01)
        handshakePayload.add(0x00)

        // Extensions length
        handshakePayload.add((extBytes.size shr 8).toByte())
        handshakePayload.add((extBytes.size and 0xFF).toByte())
        for (b in extBytes) handshakePayload.add(b)

        val hsBytes = handshakePayload.toByteArray()

        // Full TLS record
        val record = mutableListOf<Byte>()
        record.add(0x16) // ContentType: Handshake
        record.add(0x03) // Version: 3.1
        record.add(0x01)

        val hsLen = 4 + hsBytes.size
        val recLen = hsLen
        record.add((recLen shr 8).toByte())
        record.add((recLen and 0xFF).toByte())

        // Handshake Header
        record.add(0x01) // Handshake Type: ClientHello
        record.add(0x00) // 3-byte length
        record.add((hsBytes.size shr 8).toByte())
        record.add((hsBytes.size and 0xFF).toByte())

        for (b in hsBytes) record.add(b)

        return record.toByteArray()
    }
}
