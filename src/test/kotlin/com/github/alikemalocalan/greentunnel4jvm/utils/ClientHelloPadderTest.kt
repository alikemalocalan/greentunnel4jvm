package com.github.alikemalocalan.greentunnel4jvm.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientHelloPadderTest {

    @Test
    fun `pad should return non-ClientHello data untouched`() {
        val nonTls = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray()
        val result = ClientHelloPadder.pad(nonTls, targetSize = 512)
        assertArrayEquals(nonTls, result)
    }

    @Test
    fun `pad should successfully pad synthetic ClientHello to target size`() {
        val original = buildSyntheticClientHello(hostname = "example.com", includePaddingExt = false)
        val originalSize = original.size
        assertTrue(originalSize < 512)

        val padded = ClientHelloPadder.pad(original, targetSize = 512)

        assertEquals(512, padded.size)
        assertTrue(TlsUtils.isClientHello(padded))

        // Verify Record Length (bytes 3-4)
        val recordLen = ((padded[3].toInt() and 0xFF) shl 8) or (padded[4].toInt() and 0xFF)
        assertEquals(512 - 5, recordLen)

        // Verify Handshake Length (bytes 6-8)
        val handshakeLen = ((padded[6].toInt() and 0xFF) shl 16) or
                ((padded[7].toInt() and 0xFF) shl 8) or
                (padded[8].toInt() and 0xFF)
        assertEquals(512 - 9, handshakeLen)

        // Verify SNI hostname is still extractable after padding
        val sniInfo = TlsUtils.findSniInfo(padded)
        assertNotNull(sniInfo)
        assertEquals("example.com".length, sniInfo!!.hostnameLength)
        val extractedHostname = String(padded, sniInfo.hostnameOffset, sniInfo.hostnameLength, Charsets.US_ASCII)
        assertEquals("example.com", extractedHostname)
    }

    @Test
    fun `pad should skip padding if ClientHello already contains 0x0015 padding extension`() {
        val clientHelloWithPadding = buildSyntheticClientHello(hostname = "example.com", includePaddingExt = true)
        val padded = ClientHelloPadder.pad(clientHelloWithPadding, targetSize = 512)

        // Should return original without modifying
        assertArrayEquals(clientHelloWithPadding, padded)
    }

    private fun buildSyntheticClientHello(hostname: String, includePaddingExt: Boolean): ByteArray {
        val extensionsData = mutableListOf<Byte>()

        // SNI Extension
        val hostBytes = hostname.toByteArray(Charsets.US_ASCII)
        val sniExtDataLen = 2 + 1 + 2 + hostBytes.size
        extensionsData.add(0x00)
        extensionsData.add(0x00)
        extensionsData.add((sniExtDataLen shr 8).toByte())
        extensionsData.add((sniExtDataLen and 0xFF).toByte())

        val sniListLen = 1 + 2 + hostBytes.size
        extensionsData.add((sniListLen shr 8).toByte())
        extensionsData.add((sniListLen and 0xFF).toByte())
        extensionsData.add(0x00) // host_name type
        extensionsData.add((hostBytes.size shr 8).toByte())
        extensionsData.add((hostBytes.size and 0xFF).toByte())
        for (b in hostBytes) extensionsData.add(b)

        if (includePaddingExt) {
            // Padding Extension 0x0015 with 10 bytes payload
            extensionsData.add(0x00)
            extensionsData.add(0x15)
            extensionsData.add(0x00)
            extensionsData.add(0x0A) // 10 bytes
            for (i in 0 until 10) extensionsData.add(0x00)
        }

        val extBytes = extensionsData.toByteArray()

        val handshakePayload = mutableListOf<Byte>()
        handshakePayload.add(0x03)
        handshakePayload.add(0x03)
        for (i in 0 until 32) handshakePayload.add(i.toByte())
        handshakePayload.add(0x00) // Session ID len = 0
        handshakePayload.add(0x00) // Cipher suites len = 2
        handshakePayload.add(0x02)
        handshakePayload.add(0x00)
        handshakePayload.add(0x2F)
        handshakePayload.add(0x01) // Compression methods len = 1
        handshakePayload.add(0x00)

        handshakePayload.add((extBytes.size shr 8).toByte())
        handshakePayload.add((extBytes.size and 0xFF).toByte())
        for (b in extBytes) handshakePayload.add(b)

        val hsBytes = handshakePayload.toByteArray()

        val record = mutableListOf<Byte>()
        record.add(0x16)
        record.add(0x03)
        record.add(0x01)

        val hsLen = 4 + hsBytes.size
        val recLen = hsLen
        record.add((recLen shr 8).toByte())
        record.add((recLen and 0xFF).toByte())

        record.add(0x01) // ClientHello
        record.add(0x00)
        record.add((hsBytes.size shr 8).toByte())
        record.add((hsBytes.size and 0xFF).toByte())

        for (b in hsBytes) record.add(b)

        return record.toByteArray()
    }
}
