package com.github.alikemalocalan.greentunnel4jvm.log;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.github.alikemalocalan.greentunnel4jvm.gui.Gui;

/**
 * @author Rodrigo Garcia Lima (email: rodgarcialima@gmail.com | github: rodgarcialima)
 * @see ch.qos.logback.core.AppenderBase
 */
public class Appender extends AppenderBase<ILoggingEvent> {

    private PatternLayout patternLayout;

    @Override
    public void start() {
        patternLayout = new PatternLayout();
        patternLayout.setContext(getContext());
        patternLayout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        patternLayout.start();

        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String formattedMsg = patternLayout.doLayout(event);

        Gui.getMainFrom().appendLogToGui(formattedMsg, event.getLevel());
    }
}
