package com.github.alikemalocalan.greentunnel4jvm.utils

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import org.slf4j.LoggerFactory
import kotlin.random.Random

/**
 * TLS ClientHello parsing and SNI-targeted fragmentation utilities.
 *
 * Used to bypass DPI (Deep Packet Inspection) systems that inspect
 * the SNI field in TLS ClientHello to identify and block domains.
 *
 * Three layers of DPI bypass are applied:
 * 1. TLS Record Fragmentation: Splits ClientHello into two separate TLS records at the SNI midpoint
 * 2. TCP Segmentation: Each TLS record is further split into small TCP segments (existing behavior)
 * 3. Inter-Fragment Delay: Timing gap between records triggers DPI reassembly timeout
 */
object TlsUtils {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private const val TLS_CONTENT_TYPE_HANDSHAKE: Int = 0x16
    private const val HANDSHAKE_TYPE_CLIENT_HELLO: Int = 0x01
    private const val SNI_EXTENSION_TYPE: Int = 0x0000
    private const val TLS_RECORD_HEADER_SIZE: Int = 5

    /**
     * SNI hostname location within a TLS ClientHello.
     *
     * @param hostnameOffset absolute byte offset of the hostname within the TLS record
     * @param hostnameLength byte length of the hostname string
     */
    data class SniInfo(val hostnameOffset: Int, val hostnameLength: Int)

    /**
     * Checks if the data in a ByteBuf starts with a TLS ClientHello.
     * Peeks at bytes without modifying the reader index.
     *
     * Checks:
     * - Byte 0 = 0x16 (TLS Handshake content type)
     * - Byte 5 = 0x01 (ClientHello handshake type)
     */
    fun isClientHello(buf: ByteBuf): Boolean {
        if (buf.readableBytes() < 6) return false
        val readerIndex = buf.readerIndex()
        return (buf.getByte(readerIndex).toInt() and 0xFF) == TLS_CONTENT_TYPE_HANDSHAKE
                && (buf.getByte(readerIndex + 5).toInt() and 0xFF) == HANDSHAKE_TYPE_CLIENT_HELLO
    }

    /**
     * Finds the SNI hostname within a TLS ClientHello byte array.
     *
     * Parse sequence through TLS ClientHello binary structure:
     * 1. TLS Record header (5 bytes): ContentType, Version, Length
     * 2. Handshake header (4 bytes): Type, Length
     * 3. Client Version (2 bytes)
     * 4. Client Random (32 bytes)
     * 5. Session ID (variable, 1-byte length prefix)
     * 6. Cipher Suites (variable, 2-byte length prefix)
     * 7. Compression Methods (variable, 1-byte length prefix)
     * 8. Extensions (scan for type 0x0000 = SNI)
     *    - SNI Extension: List Length (2) + Name Type (1) + Name Length (2) + Hostname
     *
     * @return SniInfo with hostname offset and length, or null if not found/parseable
     */
    fun findSniInfo(data: ByteArray): SniInfo? {
        try {
            if (data.size < 6) return null
            if ((data[0].toInt() and 0xFF) != TLS_CONTENT_TYPE_HANDSHAKE) return null
            if ((data[5].toInt() and 0xFF) != HANDSHAKE_TYPE_CLIENT_HELLO) return null

            var pos = TLS_RECORD_HEADER_SIZE // Skip TLS record header (5 bytes)

            // Handshake header: type(1) + length(3)
            pos += 4

            // Client version(2) + random(32)
            pos += 34

            // Session ID: length(1) + data(N)
            if (pos >= data.size) return null
            val sessionIdLen = data[pos].toInt() and 0xFF
            pos += 1 + sessionIdLen

            // Cipher suites: length(2) + data(M)
            if (pos + 2 > data.size) return null
            val cipherSuitesLen = readUint16(data, pos)
            pos += 2 + cipherSuitesLen

            // Compression methods: length(1) + data(K)
            if (pos >= data.size) return null
            val compressionLen = data[pos].toInt() and 0xFF
            pos += 1 + compressionLen

            // Extensions: length(2) + data(E)
            if (pos + 2 > data.size) return null
            val extensionsLen = readUint16(data, pos)
            pos += 2

            val extensionsEnd = pos + extensionsLen

            // Scan each extension
            while (pos + 4 <= extensionsEnd && pos + 4 <= data.size) {
                val extType = readUint16(data, pos)
                val extDataLen = readUint16(data, pos + 2)
                pos += 4

                if (extType == SNI_EXTENSION_TYPE) {
                    // SNI extension data layout:
                    //   SNI List Length (2 bytes)
                    //   Host Name Type  (1 byte, 0x00 = DNS hostname)
                    //   Host Name Length (2 bytes)
                    //   Host Name       (variable)
                    if (pos + 5 > data.size) return null
                    val hostnameLen = readUint16(data, pos + 3)
                    val hostnameOffset = pos + 5

                    if (hostnameOffset + hostnameLen > data.size) return null

                    if (logger.isDebugEnabled) {
                        val hostname = String(data, hostnameOffset, hostnameLen, Charsets.US_ASCII)
                        logger.debug("SNI found at offset {}: {}", hostnameOffset, hostname)
                    }
                    return SniInfo(hostnameOffset, hostnameLen)
                }

                pos += extDataLen
            }

            return null
        } catch (e: Exception) {
            logger.debug("Failed to parse ClientHello: {}", e.message)
            return null
        }
    }

    /**
     * Applies SNI-targeted fragmentation with TLS record layer splitting and inter-fragment delay.
     *
     * Protection layers:
     * 1. TLS Record Fragmentation — Splits the ClientHello into two separate TLS records
     *    at the SNI hostname midpoint. DPI must perform TLS-level reassembly to see the full SNI.
     * 2. TCP Segmentation — Each TLS record is further split into small random-sized TCP segments
     *    via [HttpServiceUtils.splitAndWriteByteBuf].
     * 3. Inter-Fragment Delay — A random 1-30ms pause between the two TLS records triggers
     *    DPI reassembly timeout, causing it to skip inspection.
     *
     * Falls back to standard random TCP fragmentation if SNI cannot be located.
     *
     * @param buf       ByteBuf containing the TLS ClientHello
     * @param channel   the remote channel to write fragments to
     * @param delayMs   range of random delay between TLS records (milliseconds)
     */
    fun splitAtSni(
        buf: ByteBuf,
        channel: Channel,
        delayMs: LongRange = 1L..30L
    ) {
        val readable = buf.readableBytes()
        if (readable == 0) {
            buf.release()
            return
        }

        // Read bytes for parsing without advancing reader index
        val bytes = ByteArray(readable)
        buf.getBytes(buf.readerIndex(), bytes)

        val sniInfo = findSniInfo(bytes)

        if (sniInfo != null && sniInfo.hostnameLength > 4) {
            // Cut 3-8 bytes into the hostname (e.g., "youtube.com" → "yout|ube.com")
            val maxCut = minOf(8, sniInfo.hostnameLength - 1)
            val cutInSni = Random.nextInt(3, maxCut + 1)
            val splitPoint = sniInfo.hostnameOffset + cutInSni

            if (splitPoint > TLS_RECORD_HEADER_SIZE && splitPoint < readable) {
                // Release original buffer — we create new ones from byte arrays
                buf.release()

                // Layer 1: TLS Record Layer Fragmentation at SNI midpoint
                val tlsRecords = TlsRecordFragmenter.fragmentAtOffset(bytes, splitPoint)

                // Layer 2+3: Send first TLS record with TCP fragmentation
                val firstRecordBuf = Unpooled.wrappedBuffer(tlsRecords[0])
                HttpServiceUtils.splitAndWriteByteBuf(firstRecordBuf, channel)

                // Layer 3: Inter-fragment delay to trigger DPI reassembly timeout
                val delay = Random.nextLong(delayMs.first, delayMs.last + 1)
                Thread.sleep(delay)

                // Send remaining TLS records with TCP fragmentation
                for (i in 1 until tlsRecords.size) {
                    val recordBuf = Unpooled.wrappedBuffer(tlsRecords[i])
                    HttpServiceUtils.splitAndWriteByteBuf(recordBuf, channel)
                }

                logger.debug(
                    "DPI bypass applied: SNI split at byte {}, delay={}ms, {} TLS records, {} total bytes",
                    splitPoint, delay, tlsRecords.size, readable
                )
                return
            }
        }

        // Fallback: SNI not found or split point invalid — use random TCP fragmentation
        logger.debug("SNI not found in ClientHello ({} bytes), using random TCP fragmentation", readable)
        HttpServiceUtils.splitAndWriteByteBuf(buf, channel)
    }

    private fun readUint16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
    }
}
