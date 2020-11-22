package com.github.alikemalocalan.greentunnel4jvm.gui

import ch.qos.logback.classic.util.ContextInitializer
import com.github.alikemalocalan.greentunnel4jvm.MainForm
import javax.swing.SwingUtilities

object Gui {

    @JvmStatic
    val mainFrom = MainForm()

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "src/main/resources/gui-log-config.xml")

        SwingUtilities.invokeAndWait {
            mainFrom
        }
    }
}