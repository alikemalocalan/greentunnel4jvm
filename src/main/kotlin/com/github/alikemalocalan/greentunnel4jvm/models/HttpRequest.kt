package com.github.alikemalocalan.greentunnel4jvm.models

import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import okio.internal.commonAsUtf8ToByteArray
import java.net.URI

data class HttpRequest(
    val method: String,
    val uri: URI,
    val protocolVersion: String,
    val port: Int,
    val isHttps: Boolean,
    val headers: Option<List<Pair<String, String>>> = None,
    val payload: Option<String> = None
) {

    private fun headersAsString(): String =
        headers.map { headersNonNull ->
            headersNonNull.reversed()
                .filterNot { h -> h.first == "Client-IP" || h.second == "X-Forwarded-For" }
                .joinToString(separator = "\r\n", postfix = "\r\n") { header ->
                    String.format("%s: %s", header.first, header.second.trim())
                }
        }.getOrElse { "" }

    private fun getPath(): String =
        if (uri.path.isNullOrBlank()) "/"
        else uri.path


    fun host(): String = uri.host

    override fun toString(): String = String.format(
        "%s %s %s\r\n",
        method,
        getPath(),
        protocolVersion
    ) + headersAsString() + "\r\n" + payload.getOrElse { "" }

    fun toStringForHTTPS(): String = String.format("CONNECT %s:%s %s\r\n", host(), port, protocolVersion)

    fun toByteBuf(): ByteBuf =
        if (isHttps) Unpooled.EMPTY_BUFFER
        else Unpooled.wrappedBuffer(toString().commonAsUtf8ToByteArray())
}
