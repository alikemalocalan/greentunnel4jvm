package com.github.alikemalocalan.greentunnel4jvm.gui

import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import com.github.alikemalocalan.greentunnel4jvm.utils.SystemProxyUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*

class MainForm : JFrame() {
    val loggerText = LoggerTextPanel()
    private val panel = JPanel()
    private val scrollPane = JScrollPane(loggerText)
    private val portLabel = JLabel("Proxy Port :")
    private val button = JButton("Start")
    private val portInputField = JTextField("8080", 6)
    private val systemProxyCheckBox = JCheckBox("Auto System Proxy", false)
    private val aggressiveModeCheckBox = JCheckBox("Aggressive Mode (Padding)", false)
    private val WINDOW_WIDTH = 750
    private val WINDOW_HEIGHT = 400

    @Volatile
    private var serverThread: ServerThread? = null
    private var port: Int = 0

    init {
        button.addActionListener { e -> startServerButtonListener(e) }

        SwingUtilities.invokeLater {
            panel.add(portLabel)
            panel.add(portInputField)
            panel.add(systemProxyCheckBox)
            panel.add(aggressiveModeCheckBox)
            panel.add(button)
            add(panel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            title = "Greentunnel Proxy"
            defaultCloseOperation = EXIT_ON_CLOSE
            isResizable = true
            size = Dimension(WINDOW_WIDTH, WINDOW_HEIGHT)
            isVisible = true
            setLocationRelativeTo(null)
        }
    }

    private fun startServerButtonListener(e: ActionEvent) {
        try {
            if (serverThread == null) {
                val port = HttpServiceUtils.availablePort(portInputField.text)
                this.port = port
                serverThread = ServerThread("ServerThread", this.port, aggressiveModeCheckBox.isSelected).also { it.start() }
                if (systemProxyCheckBox.isSelected) {
                    SystemProxyUtil.getSystemProxySetting().enableProxy(this.port)
                }
                SwingUtilities.invokeLater {
                    portInputField.text = port.toString()
                    button.text = "Stop"
                }
            } else {
                if (systemProxyCheckBox.isSelected) {
                    SystemProxyUtil.getSystemProxySetting().disableProxy()
                }
                serverThread?.stopServer()
                serverThread = null
                SwingUtilities.invokeLater {
                    button.text = "Start"
                }
            }
        } catch (_: IllegalArgumentException) {
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(null, "Enter valid Port number !!!")
                portInputField.text = "8080"
            }
        }
    }
}
