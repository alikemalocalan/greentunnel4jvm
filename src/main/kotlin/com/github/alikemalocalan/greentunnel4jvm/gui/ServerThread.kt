package com.github.alikemalocalan.greentunnel4jvm.gui

import com.github.alikemalocalan.greentunnel4jvm.HttpProxyServer
import java.util.concurrent.atomic.AtomicBoolean

class ServerThread(name: String, private val port: Int) : Thread(name) {
    private val running: AtomicBoolean = AtomicBoolean(false)

    private val serverBuilder = HttpProxyServer()

    override fun run() {
        running.set(true)
        serverBuilder.createNettyServer(port)
    }

    fun stopServer() {
        serverBuilder.stop()
        running.set(false)
    }

    fun isRunning(): Boolean {
        return running.get()
    }
}