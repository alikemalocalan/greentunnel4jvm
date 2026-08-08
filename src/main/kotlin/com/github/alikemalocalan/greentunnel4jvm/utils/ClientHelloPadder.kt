package com.github.alikemalocalan.greentunnel4jvm.utils

import org.slf4j.LoggerFactory

/**
 * TLS ClientHello Padding implementation (RFC 7685).
 *
 * Used in Aggressive Mode to pad ClientHello packets to a standard size (e.g. 512 bytes),
 * frustrating DPI middleboxes that rely on exact packet size fingerprinting to identify proxies/clients.
 *
 * Safety features:
 * - Duplicate Extension Guard: Checks if padding extension (0x0015) already exists
 * - Fail-Safe Fallback: Returns original data unmodified on any parse or bounds error
 * - Precise Header Updates: Safely updates TLS Record Length, Handshake Length, and Extensions Length
 */
object ClientHelloPadder {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private const val TLS_CONTENT_TYPE_HANDSHAKE: Byte = 0x16
    private const val HANDSHAKE_TYPE_CLIENT_HELLO: Byte = 0x01
    private const val PADDING_EXTENSION_TYPE: Int = 0x0015
    private const val TLS_RECORD_HEADER_SIZE: Int = 5
    private const val DEFAULT_TARGET_SIZE: Int = 512

    /**
     * Pads a TLS ClientHello byte array to [targetSize] bytes using TLS Padding Extension (0x0015).
     *
     * @param data        the original TLS ClientHello record byte array
     * @param targetSize  desired target byte size (defaults to 512 bytes)
     * @return padded byte array, or original array if padding is skipped or unparseable
     */
    fun pad(data: ByteArray, targetSize: Int = DEFAULT_TARGET_SIZE): ByteArray {
        try {
            if (!TlsUtils.isClientHello(data)) return data

            // Check if padding extension 0x0015 already exists
            if (hasPaddingExtension(data)) {
                logger.debug("ClientHello already contains padding extension (0x0015), skipping padding")
                return data
            }

            // RFC 7685: Standard padding target is 512 bytes (to avoid F5 middlebox bugs).
            // If ClientHello is already >= 512 bytes, padding is unnecessary and could cause server handshake failures.
            if (data.size >= targetSize) {
                logger.debug("ClientHello size ({} bytes) is already >= {} bytes, skipping padding", data.size, targetSize)
                return data
            }

            var paddingNeeded = targetSize - data.size
            if (paddingNeeded < 4) {
                // Extension header requires 4 bytes (2 type + 2 length)
                return data
            }

            val extensionsLenOffset = findExtensionsLengthOffset(data) ?: return data
            val origExtensionsLen = readUint16(data, extensionsLenOffset)
            val extEnd = extensionsLenOffset + 2 + origExtensionsLen

            if (extEnd > data.size) {
                logger.debug("ClientHello extensions boundary extends beyond data size, skipping padding")
                return data
            }

            // Build padding extension: Type(2) + Length(2) + Payload(paddingNeeded - 4 zeros)
            val paddingExtDataLen = paddingNeeded - 4
            val paddingExtension = ByteArray(paddingNeeded)
            paddingExtension[0] = ((PADDING_EXTENSION_TYPE shr 8) and 0xFF).toByte()
            paddingExtension[1] = (PADDING_EXTENSION_TYPE and 0xFF).toByte()
            paddingExtension[2] = ((paddingExtDataLen shr 8) and 0xFF).toByte()
            paddingExtension[3] = (paddingExtDataLen and 0xFF).toByte()
            // Bytes 4..paddingNeeded-1 are 0x00

            // Create padded byte array and insert padding extension at extEnd
            val padded = ByteArray(data.size + paddingNeeded)
            System.arraycopy(data, 0, padded, 0, extEnd)
            System.arraycopy(paddingExtension, 0, padded, extEnd, paddingNeeded)
            if (data.size > extEnd) {
                System.arraycopy(data, extEnd, padded, extEnd + paddingNeeded, data.size - extEnd)
            }

            // 1. Update TLS Record Length (bytes 3-4)
            val newRecordLen = padded.size - TLS_RECORD_HEADER_SIZE
            padded[3] = ((newRecordLen shr 8) and 0xFF).toByte()
            padded[4] = (newRecordLen and 0xFF).toByte()

            // 2. Update Handshake Header Length (bytes 6-8, 24-bit uint)
            val origHandshakeLen = ((data[6].toInt() and 0xFF) shl 16) or
                    ((data[7].toInt() and 0xFF) shl 8) or
                    (data[8].toInt() and 0xFF)
            val newHandshakeLen = origHandshakeLen + paddingNeeded
            padded[6] = ((newHandshakeLen shr 16) and 0xFF).toByte()
            padded[7] = ((newHandshakeLen shr 8) and 0xFF).toByte()
            padded[8] = (newHandshakeLen and 0xFF).toByte()

            // 3. Update Extensions Length (bytes at extensionsLenOffset)
            val newExtensionsLen = origExtensionsLen + paddingNeeded
            padded[extensionsLenOffset] = ((newExtensionsLen shr 8) and 0xFF).toByte()
            padded[extensionsLenOffset + 1] = (newExtensionsLen and 0xFF).toByte()

            logger.info(
                "Aggressive Mode: Padded ClientHello from {} to {} bytes (+{} padding bytes)",
                data.size, padded.size, paddingNeeded
            )

            return padded
        } catch (e: Exception) {
            logger.debug("Failed to pad ClientHello, returning original: {}", e.message)
            return data
        }
    }

    /**
     * Scans ClientHello extensions to check if Extension 0x0015 (Padding) is already present.
     */
    private fun hasPaddingExtension(data: ByteArray): Boolean {
        val extLenOffset = findExtensionsLengthOffset(data) ?: return false
        val extensionsLen = readUint16(data, extLenOffset)
        var pos = extLenOffset + 2
        val end = pos + extensionsLen

        while (pos + 4 <= end && pos + 4 <= data.size) {
            val extType = readUint16(data, pos)
            val extDataLen = readUint16(data, pos + 2)
            if (extType == PADDING_EXTENSION_TYPE) {
                return true
            }
            pos += 4 + extDataLen
        }
        return false
    }

    /**
     * Locates the offset of the Extensions Length (2 bytes) within a ClientHello.
     */
    private fun findExtensionsLengthOffset(data: ByteArray): Int? {
        if (data.size < 44) return null

        var pos = TLS_RECORD_HEADER_SIZE // Skip 5-byte TLS record header

        // Handshake Header: type(1) + length(3)
        pos += 4

        // Client Version (2) + Random (32)
        pos += 34

        // Session ID
        if (pos >= data.size) return null
        val sessionIdLen = data[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen

        // Cipher Suites
        if (pos + 2 > data.size) return null
        val cipherSuitesLen = readUint16(data, pos)
        pos += 2 + cipherSuitesLen

        // Compression Methods
        if (pos >= data.size) return null
        val compressionLen = data[pos].toInt() and 0xFF
        pos += 1 + compressionLen

        // Extensions Length Offset
        if (pos + 2 > data.size) return null
        return pos
    }

    private fun readUint16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
    }
}
