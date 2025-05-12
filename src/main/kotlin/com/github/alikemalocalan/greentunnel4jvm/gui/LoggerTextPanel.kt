package com.github.alikemalocalan.greentunnel4jvm.gui

import java.awt.Color
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.*

class LoggerTextPanel : JTextPane() {

    private val logLimit = 1000
    private val lineLimit = 200

    private val ERROR_ATT = SimpleAttributeSet()
    private val WARN_ATT = SimpleAttributeSet()
    private val INFO_ATT = SimpleAttributeSet()
    private val DEBUG_ATT = SimpleAttributeSet()
    private val TRACE_ATT = SimpleAttributeSet()
    private val RESTO_ATT = SimpleAttributeSet()

    init {
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

    }

    fun appendLogToGui(logMessage: String, eventLevel: String) {
        try {
            if (this.document.defaultRootElement.elementCount > logLimit) {
                val end = getLineEndOffset(this, lineLimit)
                replaceRange(this, null, 0, end)
            }

            val att = when (eventLevel) {
                "ERROR" -> ERROR_ATT
                "WARN" -> WARN_ATT
                "INFO" -> INFO_ATT
                "DEBUG" -> DEBUG_ATT
                "TRACE" -> TRACE_ATT
                else -> RESTO_ATT
            }

            SwingUtilities.invokeLater {
                this.document.insertString(this.document.length, logMessage, att)
            }
        } catch (e: BadLocationException) {
            e.printStackTrace()
        }
        SwingUtilities.invokeLater {
            this.caretPosition = this.document.length
        }
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
        SwingUtilities.invokeLater {
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

    private fun getLineCount(textPane: JTextPane): Int {
        return textPane.document.defaultRootElement.elementCount
    }

}