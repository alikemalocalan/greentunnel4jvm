package com.github.alikemalocalan.greentunnel4jvm.gui

import ch.qos.logback.classic.ClassicConstants
import javax.swing.SwingUtilities

object Gui {

    @JvmStatic
    val mainForm = MainForm()

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "gui-log-config.xml")

        SwingUtilities.invokeAndWait {
            mainForm
        }
    }
}