package com.github.alikemalocalan.greentunnel4jvm.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xbill.DNS.*
import java.net.InetAddress
import java.time.Duration
import java.util.*

object DNSOverHttps {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private const val DOH_URL: String = "https://dns.google/dns-query"
    private val dohResolver = DohResolver(DOH_URL, 100, Duration.ofMinutes(2))
    private val cache = Cache()

    @JvmStatic
    fun lookUp(address: String): Optional<InetAddress> {
        return try {
            val lookup = Lookup(address, Type.A)
            lookup.setResolver(dohResolver)
            lookup.setCache(cache)
            val result = lookup.run()
            if (result.isNullOrEmpty()) {
                logger.error("Ip address not found for : $address")
                Optional.empty()
            } else {
                val record: Record = result[0]
                val ip = InetAddress.getByName(record.rdataToString())
                Optional.of(ip)
            }
        } catch (e: TextParseException) {
            logger.error("Invalid address: $address , error: ${e.localizedMessage}")
            Optional.empty()
        } catch (e: Exception) {
            logger.error("Error looking up address: $address , error: ${e.localizedMessage}")
            Optional.empty()
        }
    }
}