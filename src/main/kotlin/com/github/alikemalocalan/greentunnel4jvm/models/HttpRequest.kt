package com.github.alikemalocalan.greentunnel4jvm.models

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import okio.internal.commonAsUtf8ToByteArray
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
            headersNonNull.reversed()
                .joinToString(separator = "\r\n", postfix = "\r\n") { header ->
                    String.format("%s: %s", header.first, header.second.trim())
                }
        }.orElseGet { "" }

    private fun getPath(): String =
        if (uri.path.isNullOrBlank()) "/"
        else uri.path


    fun host(): String = uri.host

    override fun toString(): String = String.format(
        "%s  %s  %s\r\n",
        method,
        getPath(),
        protocolVersion
    ) + headersAsString() + "\r\n" + payload

    fun toByteBuf(): ByteBuf =
        if (isHttps) Unpooled.EMPTY_BUFFER
        else Unpooled.wrappedBuffer(toString().commonAsUtf8ToByteArray())
}
