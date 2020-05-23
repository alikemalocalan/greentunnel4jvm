package com.github.alikemalocalan.greentunnel4jvm.utils

import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit.SECONDS

object DNSOverHttps {
    var logging: HttpLoggingInterceptor = HttpLoggingInterceptor()

    val cache = Cache(
        directory = File(System.getProperty("java.io.tmpdir"), "http_cache"),
        // $0.05 worth of phone storage in 2020
        maxSize = 50L * 1024L * 1024L // 10 MiB
    )

    private val client by lazy {
        logging.setLevel(HttpLoggingInterceptor.Level.NONE)
        OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(logging)
            .connectTimeout(60, SECONDS)
            .writeTimeout(60, SECONDS)
            .readTimeout(60, SECONDS)
            .build()
    }

    private val dns by lazy {
        buildDnsClient(client)
    }

    fun lookUp(address: String): String =
        dns.lookup(address).first().hostAddress


    @JvmStatic
    private fun buildDnsClient(bootstrapClient: OkHttpClient): DnsOverHttps {
        return DnsOverHttps.Builder().client(bootstrapClient)
            .url("https://1.1.1.1/dns-query".toHttpUrl())
            .bootstrapDnsHosts(getByIp("1.1.1.1"), getByIp("1.0.0.1"))
            .includeIPv6(false)
            .post(true)
            .build()
    }

    @JvmStatic
    private fun getByIp(host: String): InetAddress {
        return try {
            InetAddress.getByName(host)
        } catch (e: UnknownHostException) {
            // unlikely
            throw RuntimeException(e)
        }
    }
}