package com.github.alikemalocalan.greentunnel4jvm.utils

import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

object DNSOverHttps {

    private val cache = Cache(
        directory = File(System.getProperty("java.io.tmpdir"), "http_cache"),
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
            .url("https://doh.centraleu.pi-dns.com/dns-query".toHttpUrl()) // TODO add more option for here
            .includeIPv6(false)
            .build()

    @JvmStatic
    fun lookUp(address: String): String =
        dns.lookup(address).first().hostAddress
}