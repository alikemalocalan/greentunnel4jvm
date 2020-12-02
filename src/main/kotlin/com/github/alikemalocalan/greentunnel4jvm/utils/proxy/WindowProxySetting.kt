package com.github.alikemalocalan.greentunnel4jvm.utils.proxy

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WindowProxySetting : ProxySetting {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun enableProxy(port: Int) {
        logger.warn("You must change your browser proxy setting with : 127.0.0.1:8080")
    }

    override fun disableProxy() {
        logger.warn("You can disable your browser proxy setting")
    }
}