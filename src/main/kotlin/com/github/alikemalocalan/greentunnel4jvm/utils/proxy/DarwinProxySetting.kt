package com.github.alikemalocalan.greentunnel4jvm.utils.proxy

import com.github.alikemalocalan.greentunnel4jvm.utils.SystemProxyUtil

class DarwinProxySetting : ProxySetting {

    private val interFace = SystemProxyUtil.runCommand("route -n get 0.0.0.0 | grep 'interface' | cut -d ':' -f2")

    private val wifiAdapter: String =
        SystemProxyUtil.runCommand("networksetup -listnetworkserviceorder | grep $interFace -B 1 | head -n 1 | cut -d ' ' -f2")


    override fun enableProxy(port: Int) {
        SystemProxyUtil.runCommand("networksetup -setwebproxy '${wifiAdapter}' 127.0.0.1 $port")
        SystemProxyUtil.runCommand("networksetup -setsecurewebproxy '${wifiAdapter}' 127.0.0.1 $port")
    }

    override fun disableProxy() {
        SystemProxyUtil.runCommand("networksetup -setwebproxystate '${wifiAdapter}' off")
        SystemProxyUtil.runCommand("networksetup -setsecurewebproxystate '${wifiAdapter}' off")
    }
}