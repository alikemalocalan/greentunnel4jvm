package com.github.alikemalocalan.greentunnel4jvm.utils.proxy

import com.github.alikemalocalan.greentunnel4jvm.utils.SystemProxyUtil

class LinuxProxySetting : ProxySetting {
    override fun enableProxy(port: Int) {
        // Disabled for Linux desktop/CLI for now
    }

    override fun disableProxy() {
        // Disabled for Linux desktop/CLI for now
    }
}