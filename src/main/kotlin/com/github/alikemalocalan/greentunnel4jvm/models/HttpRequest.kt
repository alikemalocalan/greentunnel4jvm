package com.github.alikemalocalan.greentunnel4jvm.models

import com.github.alikemalocalan.greentunnel4jvm.utils.DNSOverHttps
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import okio.internal.commonAsUtf8ToByteArray
import java.net.InetSocketAddress
import java.net.URI
import java.util.*

data class HttpRequest(
    val method: String,
    val uri: URI,
    val protocolVersion: String,
    val port: Int,
    val isHttps: Boolean,
    val headers: Optional<List<Pair<String, String>>> = Optional.empty(),
    val payload: Optional<String> = Optional.empty()
) {

    private fun headersAsString(): String =
        headers.map { headersNonNull ->
            "\r\n" + headersNonNull.reversed()
                .joinToString(separator = "\r\n", postfix = "\r\n") { header ->
                    String.format("%s: %s", header.first, header.second.trim())
                }
        }.orElseGet { "" }

    private fun getPath(): String =
        if (uri.path.isNullOrBlank()) "/"
        else uri.path


    fun host(): String = uri.host

    override fun toString(): String = String.format(
        "%s  %s  %s%s%s",
        method,
        getPath(),
        protocolVersion,
        headersAsString(),
        payload.map { payload -> "\r\n" + payload }.orElseGet { "" }
    )

    fun toByteBuf(): ByteBuf =
        if (isHttps) Unpooled.EMPTY_BUFFER
        else Unpooled.wrappedBuffer(toString().commonAsUtf8ToByteArray())

    fun toInetSocketAddress() = InetSocketAddress(DNSOverHttps.lookUp(host()), port)

}
