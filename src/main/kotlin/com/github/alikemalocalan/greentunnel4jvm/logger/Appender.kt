package com.github.alikemalocalan.greentunnel4jvm.logger

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.github.alikemalocalan.greentunnel4jvm.gui.Gui
import javax.swing.SwingUtilities

class Appender : AppenderBase<ILoggingEvent>() {
    private var patternLayout: PatternLayout? = null

    override fun start() {
        patternLayout = CustomPatternLayout(this@Appender.context)
        patternLayout?.start()
        super.start()
    }

    override fun append(event: ILoggingEvent) {
        val formattedMsg = patternLayout?.doLayout(event) ?: return
        SwingUtilities.invokeLater {
            Gui.mainForm.loggerText.appendLogToGui(formattedMsg, event.level.levelStr)
        }
    }
}