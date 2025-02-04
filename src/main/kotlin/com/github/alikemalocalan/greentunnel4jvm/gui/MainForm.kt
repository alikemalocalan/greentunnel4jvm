package com.github.alikemalocalan.greentunnel4jvm.gui

import ch.qos.logback.classic.Level
import com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils
import com.github.alikemalocalan.greentunnel4jvm.utils.SystemProxyUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.text.*

class MainForm : JFrame() {
    private val panel = JPanel()
    private val textPanel = JTextPane()
    private val scrollPane = JScrollPane(textPanel)
    private val portLabel = JLabel("Proxy Port :")
    private val button = JButton("Start")
    private val portInputField = JTextField("8080", 10)

    private val logLimit = 1000
    private val lineLimit = 200

    private val ERROR_ATT = SimpleAttributeSet()
    private val WARN_ATT = SimpleAttributeSet()
    private val INFO_ATT = SimpleAttributeSet()
    private val DEBUG_ATT = SimpleAttributeSet()
    private val TRACE_ATT = SimpleAttributeSet()
    private val RESTO_ATT = SimpleAttributeSet()

    @Volatile
    private var serverThread: ServerThread? = null
    private var port: Int = 0

    init {
        button.addActionListener { e -> startServerButtonListener(e) }

        panel.add(portLabel, BorderLayout.LINE_START)
        panel.add(portInputField, BorderLayout.LINE_START)
        panel.add(button, BorderLayout.LINE_END)
        add(panel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        title = "Greentunnel Proxy"
        defaultCloseOperation = EXIT_ON_CLOSE
        isResizable = true
        size = Dimension(600, 400)

        // ERROR
        StyleConstants.setBold(ERROR_ATT, true)
        StyleConstants.setItalic(ERROR_ATT, false)
        StyleConstants.setForeground(ERROR_ATT, Color(153, 0, 0))

        // WARN
        StyleConstants.setBold(WARN_ATT, false)
        StyleConstants.setItalic(WARN_ATT, false)
        StyleConstants.setForeground(WARN_ATT, Color(153, 76, 0))

        // INFO
        StyleConstants.setBold(INFO_ATT, false)
        StyleConstants.setItalic(INFO_ATT, false)
        StyleConstants.setForeground(INFO_ATT, Color(0, 0, 153))

        // DEBUG
        StyleConstants.setBold(DEBUG_ATT, false)
        StyleConstants.setItalic(DEBUG_ATT, true)
        StyleConstants.setForeground(DEBUG_ATT, Color(64, 64, 64))

        // TRACE
        StyleConstants.setBold(TRACE_ATT, false)
        StyleConstants.setItalic(TRACE_ATT, true)
        StyleConstants.setForeground(TRACE_ATT, Color(153, 0, 76))

        // RESTO
        StyleConstants.setBold(RESTO_ATT, false)
        StyleConstants.setItalic(RESTO_ATT, true)
        StyleConstants.setForeground(RESTO_ATT, Color(0, 0, 0))

        isVisible = true
        setLocationRelativeTo(null)
    }

    private fun startServerButtonListener(e: ActionEvent) {
        try {
            if (serverThread == null) {
                val port = HttpServiceUtils.availablePort(portInputField.text)
                portInputField.text = port.toString()
                this.port = port
                serverThread = ServerThread("ServerThread", this.port).also { it.start() }
                SystemProxyUtil.getSystemProxySetting().enableProxy(this.port)
                button.text = "Stop"
            } else {
                SystemProxyUtil.getSystemProxySetting().disableProxy()
                serverThread?.stopServer()
                serverThread = null
                button.text = "Start"
            }
        } catch (ex: IllegalArgumentException) {
            JOptionPane.showMessageDialog(null, "Enter valid Port number !!!")
            portInputField.text = "8080"
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun appendLogToGui(logMessage: String, eventLevel: Level) {
        try {
            if (textPanel.document.defaultRootElement.elementCount > logLimit) {
                val end = getLineEndOffset(textPanel, lineLimit)
                replaceRange(textPanel, null, 0, end)
            }

            val att = when (eventLevel) {
                Level.ERROR -> ERROR_ATT
                Level.WARN -> WARN_ATT
                Level.INFO -> INFO_ATT
                Level.DEBUG -> DEBUG_ATT
                Level.TRACE -> TRACE_ATT
                else -> RESTO_ATT
            }

            textPanel.document.insertString(textPanel.document.length, logMessage, att)
        } catch (e: BadLocationException) {
            e.printStackTrace()
        }
        textPanel.caretPosition = textPanel.document.length
    }

    private fun getLineCount(textPane: JTextPane): Int {
        return textPane.document.defaultRootElement.elementCount
    }

    @Throws(BadLocationException::class)
    private fun getLineEndOffset(textPane: JTextPane, line: Int): Int {
        val lineCount = getLineCount(textPane)
        if (line < 0) {
            throw BadLocationException("Negative line", -1)
        } else if (line >= lineCount) {
            throw BadLocationException("No such line", textPane.document.length + 1)
        }
        val map = textPane.document.defaultRootElement
        val lineElem = map.getElement(line)
        val endOffset = lineElem.endOffset
        return if (line == lineCount - 1) endOffset - 1 else endOffset
    }

    @Throws(IllegalArgumentException::class)
    private fun replaceRange(textPane: JTextPane, str: String?, start: Int, end: Int) {
        if (end < start) {
            throw IllegalArgumentException("end before start")
        }
        val doc: Document = textPane.document
        try {
            if (doc is AbstractDocument) {
                doc.replace(start, end - start, str, null)
            } else {
                doc.remove(start, end - start)
                doc.insertString(start, str, null)
            }
        } catch (e: BadLocationException) {
            throw IllegalArgumentException(e.message)
        }
    }
}