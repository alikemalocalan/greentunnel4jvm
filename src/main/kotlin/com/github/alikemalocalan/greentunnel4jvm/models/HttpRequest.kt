package com.github.alikemal.greentunnelmobil.tunnel.models

import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaders
import java.net.URI

data class HttpRequest(
    val method: String,
    val uri: URI,
    val protocolVersion: String,
    val port: Int,
    val isHttps: Boolean,
    val headers: Option<HttpHeaders> = None,
    val payload: Option<String> = None
) {

    fun headersAsString(): String {
        val builder = StringBuilder("")

        return headers.map { headersNonNull ->
            headersNonNull.forEach { header ->
                builder.append(String.format("%s: %s\r\n", header.key, header.value.trim()))
            }
            builder.toString()
        }.getOrElse { "" }
    }


    fun getPath(): String =
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

    fun toByteBuf(): ByteBuf = if (isHttps) Unpooled.EMPTY_BUFFER else Unpooled.wrappedBuffer(toString().toByteArray())
}
