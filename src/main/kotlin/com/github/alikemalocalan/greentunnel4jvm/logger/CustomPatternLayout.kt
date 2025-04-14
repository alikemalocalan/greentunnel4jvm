package com.github.alikemalocalan.greentunnel4jvm.logger

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.core.Context

class CustomPatternLayout(context: Context) : PatternLayout() {
    init {
        pattern = "%d{HH:mm:ss.SSS} | %-5level | %logger{36} | %msg%n"
        this.context = context
    }
}