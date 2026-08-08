package com.github.alikemalocalan.greentunnel4jvm.utils

import org.slf4j.LoggerFactory

/**
 * Splits a single TLS record into multiple valid TLS records.
 *
 * This operates at the TLS record layer (Layer 5), which is different from
 * TCP segmentation (Layer 4). Even if a DPI system performs TCP reassembly,
 * it still sees multiple separate TLS records that require TLS-level reassembly
 * to reconstruct the original handshake message — a much more expensive operation
 * that most DPI systems do not perform.
 *
 * Per RFC 8446 Section 5.1: "Handshake messages MAY be coalesced into a single
 * TLSPlaintext record or fragmented across several records."
 */
object TlsRecordFragmenter {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private const val TLS_RECORD_HEADER_SIZE = 5

    /**
     * Splits a TLS record into two records at the given absolute byte offset.
     *
     * The split creates two valid TLS records:
     * - Record 1: original payload from byte 5 to splitOffset-1
     * - Record 2: original payload from splitOffset to end
     *
     * Both records inherit the same ContentType and Version from the original.
     *
     * @param data                the complete TLS record (header + payload)
     * @param absoluteSplitOffset byte offset within the original data where the split occurs
     * @return list of TLS records (2 if split succeeded, 1 if split was not possible)
     */
    fun fragmentAtOffset(data: ByteArray, absoluteSplitOffset: Int): List<ByteArray> {
        if (data.size <= TLS_RECORD_HEADER_SIZE) {
            return listOf(data)
        }
        if (absoluteSplitOffset <= TLS_RECORD_HEADER_SIZE || absoluteSplitOffset >= data.size) {
            return listOf(data)
        }

        val contentType = data[0]
        val versionMajor = data[1]
        val versionMinor = data[2]

        val payload1 = data.copyOfRange(TLS_RECORD_HEADER_SIZE, absoluteSplitOffset)
        val payload2 = data.copyOfRange(absoluteSplitOffset, data.size)

        val record1 = buildTlsRecord(contentType, versionMajor, versionMinor, payload1)
        val record2 = buildTlsRecord(contentType, versionMajor, versionMinor, payload2)

        logger.debug(
            "TLS record split: {} bytes -> [{} + {}] bytes at offset {}",
            data.size, record1.size, record2.size, absoluteSplitOffset
        )

        return listOf(record1, record2)
    }

    /**
     * Splits a TLS record into multiple records with randomized payload sizes.
     *
     * @param data      the complete TLS record (header + payload)
     * @param sizeRange range of random payload sizes for each fragment
     * @return list of TLS records
     */
    fun fragmentRandomized(
        data: ByteArray,
        sizeRange: IntRange = 20..80
    ): List<ByteArray> {
        if (data.size <= TLS_RECORD_HEADER_SIZE) return listOf(data)
        if (data.size <= TLS_RECORD_HEADER_SIZE + sizeRange.first) return listOf(data)

        val contentType = data[0]
        val versionMajor = data[1]
        val versionMinor = data[2]
        val payload = data.copyOfRange(TLS_RECORD_HEADER_SIZE, data.size)

        val fragments = mutableListOf<ByteArray>()
        var offset = 0

        while (offset < payload.size) {
            val maxChunk = kotlin.random.Random.nextInt(sizeRange.first, sizeRange.last + 1)
            val chunkSize = minOf(maxChunk, payload.size - offset)
            val chunk = payload.copyOfRange(offset, offset + chunkSize)

            fragments.add(buildTlsRecord(contentType, versionMajor, versionMinor, chunk))
            offset += chunkSize
        }

        logger.debug(
            "TLS record randomized: {} bytes -> {} fragments",
            data.size, fragments.size
        )

        return fragments
    }

    private fun buildTlsRecord(
        contentType: Byte,
        versionMajor: Byte,
        versionMinor: Byte,
        payload: ByteArray
    ): ByteArray {
        val record = ByteArray(TLS_RECORD_HEADER_SIZE + payload.size)
        record[0] = contentType
        record[1] = versionMajor
        record[2] = versionMinor
        record[3] = ((payload.size shr 8) and 0xFF).toByte()
        record[4] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, record, TLS_RECORD_HEADER_SIZE, payload.size)
        return record
    }
}
