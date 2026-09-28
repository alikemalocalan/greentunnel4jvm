package com.github.alikemalocalan.greentunnel4jvm.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xbill.DNS.*
import org.xbill.DNS.TextParseException
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration
import java.util.*

object DNSOverHttps {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private const val DOH_URL: String = "https://dns.google/dns-query"
    private val dohResolver = DohResolver(DOH_URL, 100, Duration.ofMinutes(2))

    @Volatile
    var isDohEnabled: Boolean = true

    private val cache = Cache().apply {
        maxEntries = 500
        maxNCache = 300
    }

    private fun systemDnsLookup(cleanAddress: String): Optional<InetAddress> {
        return try {
            val ip = InetAddress.getByName(cleanAddress)
            Optional.of(ip)
        } catch (e: Exception) {
            logger.error("System DNS lookup failed for: $cleanAddress , error: ${e.localizedMessage}")
            Optional.empty()
        }
    }

    @JvmStatic
    fun lookUp(address: String): Optional<InetAddress> {
        val cleanAddress = address.trimEnd('.')

        if (!isDohEnabled) {
            return systemDnsLookup(cleanAddress)
        }

        return try {
            val lookup = Lookup(cleanAddress, Type.A)
            lookup.setResolver(dohResolver)
            lookup.setCache(cache)
            val result = lookup.run()
            if (result.isNullOrEmpty()) {
                logger.error("Ip address not found for : $cleanAddress")
                Optional.empty()
            } else {
                val record = result.filterIsInstance<ARecord>().firstOrNull()
                if (record != null) {
                    val ip = InetAddress.getByName(record.address.hostAddress)
                    Optional.of(ip)
                } else {
                    logger.error("Ip address not found for : $cleanAddress")
                    Optional.empty()
                }
            }
        } catch (e: TextParseException) {
            logger.error("Error looking up address: $cleanAddress , error: ${e.localizedMessage}")
            Optional.empty()
        }
    }
}