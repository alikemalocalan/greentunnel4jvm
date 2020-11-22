package com.github.alikemalocalan.greentunnel4jvm.utils.proxy

import com.github.alikemalocalan.greentunnel4jvm.utils.SystemProxyUtil

class LinuxProxySetting : ProxySetting {
    override fun enableProxy(port: Int) {
        SystemProxyUtil.runCommand("gsettings set org.gnome.system.proxy mode manual")
        SystemProxyUtil.runCommand("gsettings set org.gnome.system.proxy.http host 127.0.0.1")
        SystemProxyUtil.runCommand("gsettings set org.gnome.system.proxy.http port $port")
    }

    override fun disableProxy() {
        SystemProxyUtil.runCommand("gsettings set org.gnome.system.proxy mode none")
    }

}