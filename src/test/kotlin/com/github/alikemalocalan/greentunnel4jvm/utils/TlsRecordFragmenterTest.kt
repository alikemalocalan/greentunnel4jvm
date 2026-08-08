package com.github.alikemalocalan.greentunnel4jvm.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TlsRecordFragmenterTest {

    @Test
    fun `fragmentAtOffset should split record into two valid TLS records`() {
        val payload = ByteArray(100) { (it + 1).toByte() }
        val record = ByteArray(5 + payload.size)
        record[0] = 0x16.toByte() // Handshake
        record[1] = 0x03.toByte() // TLS 1.2
        record[2] = 0x03.toByte()
        record[3] = 0x00.toByte() // length = 100
        record[4] = 0x64.toByte()
        System.arraycopy(payload, 0, record, 5, 100)

        val fragments = TlsRecordFragmenter.fragmentAtOffset(record, absoluteSplitOffset = 45)

        assertEquals(2, fragments.size)

        val first = fragments[0]
        assertEquals(0x16.toByte(), first[0])
        assertEquals(0x03.toByte(), first[1])
        assertEquals(0x03.toByte(), first[2])
        val len1 = ((first[3].toInt() and 0xFF) shl 8) or (first[4].toInt() and 0xFF)
        assertEquals(40, len1) // 45 - 5

        val second = fragments[1]
        assertEquals(0x16.toByte(), second[0])
        assertEquals(0x03.toByte(), second[1])
        assertEquals(0x03.toByte(), second[2])
        val len2 = ((second[3].toInt() and 0xFF) shl 8) or (second[4].toInt() and 0xFF)
        assertEquals(60, len2) // 105 - 45

        // Verify total payload preserved
        val reassembledPayload = first.copyOfRange(5, first.size) + second.copyOfRange(5, second.size)
        assertArrayEquals(payload, reassembledPayload)
    }

    @Test
    fun `fragmentAtOffset should return original data if split offset is invalid`() {
        val record = byteArrayOf(0x16, 0x03, 0x03, 0x00, 0x04, 1, 2, 3, 4)

        // Split offset before header or past end
        val result1 = TlsRecordFragmenter.fragmentAtOffset(record, absoluteSplitOffset = 3)
        assertEquals(1, result1.size)
        assertArrayEquals(record, result1[0])

        val result2 = TlsRecordFragmenter.fragmentAtOffset(record, absoluteSplitOffset = 10)
        assertEquals(1, result2.size)
        assertArrayEquals(record, result2[0])
    }

    @Test
    fun `fragmentRandomized should split payload into multiple TLS records preserving content`() {
        val payload = ByteArray(200) { (it % 256).toByte() }
        val record = ByteArray(5 + payload.size)
        record[0] = 0x16.toByte()
        record[1] = 0x03.toByte()
        record[2] = 0x03.toByte()
        record[3] = (200 shr 8).toByte()
        record[4] = (200 and 0xFF).toByte()
        System.arraycopy(payload, 0, record, 5, 200)

        val fragments = TlsRecordFragmenter.fragmentRandomized(record, sizeRange = 20..30)

        assertTrue(fragments.size >= 7)

        val reassembled = mutableListOf<Byte>()
        for (frag in fragments) {
            assertEquals(0x16.toByte(), frag[0])
            val chunkLen = ((frag[3].toInt() and 0xFF) shl 8) or (frag[4].toInt() and 0xFF)
            assertEquals(frag.size - 5, chunkLen)
            for (i in 5 until frag.size) {
                reassembled.add(frag[i])
            }
        }

        assertArrayEquals(payload, reassembled.toByteArray())
    }
}
