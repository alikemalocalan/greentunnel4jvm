package com.github.alikemalocalan.greentunnel4jvm.utils.proxy

interface ProxySetting {
    fun enableProxy(port: Int)
    fun disableProxy()
}