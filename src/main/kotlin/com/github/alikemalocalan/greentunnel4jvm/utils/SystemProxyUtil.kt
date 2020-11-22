package com.github.alikemalocalan.greentunnel4jvm.utils

import com.github.alikemalocalan.greentunnel4jvm.utils.proxy.DarwinProxySetting
import com.github.alikemalocalan.greentunnel4jvm.utils.proxy.LinuxProxySetting
import com.github.alikemalocalan.greentunnel4jvm.utils.proxy.ProxySetting


object SystemProxyUtil {

    @JvmStatic
    fun getSystemProxySetting(): ProxySetting {
        val operSys = System.getProperty("os.name").toLowerCase()
        if (operSys.contains("win")) {
            DarwinProxySetting()
        } else if (operSys.contains("nix") || operSys.contains("nux")
            || operSys.contains("aix")
        ) {
            return LinuxProxySetting()
        } else if (operSys.contains("mac")) {
            return DarwinProxySetting()
        } else if (operSys.contains("sunos")) {
            return DarwinProxySetting()
        }
        return LinuxProxySetting()
    }


    @JvmStatic
    fun runCommand(command: String): String {
        val commands = arrayOf(
            "/bin/sh",
            "-c",
            command
        )
        println(commands.joinToString(" "))
        val process = Runtime.getRuntime().exec(commands)
        process.waitFor()
        return String(process.inputStream.readAllBytes()).trim()
    }

    @JvmStatic
    fun runCommand(commands: Array<String>): String {
        println(commands.joinToString(" "))
        val process = Runtime.getRuntime().exec(commands)
        process.waitFor()
        return String(process.inputStream.readAllBytes())
    }

}

