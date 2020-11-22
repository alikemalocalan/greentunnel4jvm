package com.github.alikemalocalan.greentunnel4jvm.gui

import com.github.alikemalocalan.greentunnel4jvm.HttpProxyServer
import io.netty.channel.ChannelFuture
import java.util.concurrent.atomic.AtomicBoolean

class ServerThread(name: String, private val port: Int) : Thread(name) {
    private val running: AtomicBoolean = AtomicBoolean(false)

    private val serverBuilder = HttpProxyServer()
    private var server: ChannelFuture? = null

    override fun run() {
        running.set(true)
        server = serverBuilder.createNettyServer(port)
    }

    fun stopServer() {
        serverBuilder.stop(server)
        this.stop()
        running.set(false)
    }

    fun isRunning(): Boolean {
        return running.get()
    }
}