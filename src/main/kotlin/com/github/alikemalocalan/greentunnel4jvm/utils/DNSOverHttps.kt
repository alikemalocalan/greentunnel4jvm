package com.github.alikemalocalan.greentunnel4jvm.utils

import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

object DNSOverHttps {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val cache = Cache(
        directory = File.createTempFile("dnsOverHttps","http_cache"),
        maxSize = 50L * 1024L * 1024L // 10 MiB
    )

    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(60, SECONDS)
            .writeTimeout(60, SECONDS)
            .readTimeout(60, SECONDS)
            .build()

    private val dns: DnsOverHttps =
        DnsOverHttps.Builder().client(client)
            .url("https://doh.familyshield.opendns.com/dns-query".toHttpUrl()) // TODO add more option for here
            .post(true)
            .build()

    @JvmStatic
    fun lookUp(address: String): Optional<String> =
        kotlin.runCatching {
            Optional.of(dns.lookup(address).first().hostAddress)
        }.onFailure { logger.error("Ip address not found for : $address") }
            .getOrDefault(Optional.empty())

}