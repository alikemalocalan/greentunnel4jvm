package com.github.alikemalocalan.greentunnel4jvm.logger

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.github.alikemalocalan.greentunnel4jvm.gui.Gui

class Appender : AppenderBase<ILoggingEvent>() {
    private lateinit var patternLayout: PatternLayout

    override fun start() {
        patternLayout = PatternLayout().apply {
            context = this@Appender.context
            pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
            start()
        }
        super.start()
    }

    override fun append(event: ILoggingEvent) {
        val formattedMsg = patternLayout.doLayout(event)
        Gui.mainForm.appendLogToGui(formattedMsg, event.level)
    }
}