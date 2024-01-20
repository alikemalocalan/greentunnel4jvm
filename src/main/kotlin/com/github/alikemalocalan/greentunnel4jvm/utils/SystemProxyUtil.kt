package com.github.alikemalocalan.greentunnel4jvm.utils

import com.github.alikemalocalan.greentunnel4jvm.utils.proxy.DarwinProxySetting
import com.github.alikemalocalan.greentunnel4jvm.utils.proxy.LinuxProxySetting
import com.github.alikemalocalan.greentunnel4jvm.utils.proxy.ProxySetting
import com.github.alikemalocalan.greentunnel4jvm.utils.proxy.WindowProxySetting
import java.io.InputStream


object SystemProxyUtil {

    @JvmStatic
    fun getSystemProxySetting(): ProxySetting {
        val operatingSystem = System.getProperty("os.name").lowercase()
        return when {
            operatingSystem.contains("win") -> WindowProxySetting()
            operatingSystem.contains("mac") -> DarwinProxySetting()
            else -> LinuxProxySetting()
        }
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
        return readAllString(process.inputStream).trim()
    }

    @JvmStatic
    private fun readAllString(inputStream: InputStream): String {
        val strArray = ByteArray(inputStream.available())
        inputStream.read(strArray)
        return String(strArray)
    }

}

