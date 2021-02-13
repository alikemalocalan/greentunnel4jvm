package com.github.alikemalocalan.greentunnel4jvm;

import ch.qos.logback.classic.Level;
import com.github.alikemalocalan.greentunnel4jvm.gui.ServerThread;
import com.github.alikemalocalan.greentunnel4jvm.utils.SystemProxyUtil;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import static com.github.alikemalocalan.greentunnel4jvm.utils.HttpServiceUtils.availablePort;
import static javax.swing.JOptionPane.showMessageDialog;

public class MainForm extends JFrame {
    JPanel panel = new JPanel();
    JTextPane textPanel = new JTextPane();
    JScrollPane scrollPane = new JScrollPane(textPanel);
    JLabel portLabel = new JLabel("Proxy Port :");
    JButton button = new JButton("Start");
    JTextField portInputField = new JTextField("8080", 10);

    int logLimit = 1000;
    int lineLimit = 200;

    private final SimpleAttributeSet ERROR_ATT = new SimpleAttributeSet();
    private final SimpleAttributeSet WARN_ATT = new SimpleAttributeSet();
    private final SimpleAttributeSet INFO_ATT = new SimpleAttributeSet();
    private final SimpleAttributeSet DEBUG_ATT = new SimpleAttributeSet();
    private final SimpleAttributeSet TRACE_ATT = new SimpleAttributeSet();
    private final SimpleAttributeSet RESTO_ATT = new SimpleAttributeSet();

    private volatile ServerThread serverThread = null;
    private int port;

    public MainForm() {
        button.addActionListener(this::startServerButtonListener);

        panel.add(portLabel, BorderLayout.LINE_START);
        panel.add(portInputField, BorderLayout.LINE_START);
        panel.add(button, BorderLayout.LINE_END);
        add(panel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        setTitle("Greentunnel Proxy");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(true);
        setSize(new Dimension(600, 400));

        // ERROR
        ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.TRUE);
        ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
        ERROR_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 0, 0));

        // WARN
        WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
        WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
        WARN_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 76, 0));

        // INFO
        INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
        INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.FALSE);
        INFO_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0, 0, 153));

        // DEBUG
        DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
        DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
        DEBUG_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(64, 64, 64));

        // TRACE
        TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
        TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
        TRACE_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(153, 0, 76));

        // RESTO
        RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.FALSE);
        RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Italic, Boolean.TRUE);
        RESTO_ATT.addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0, 0, 0));

        setVisible(true);
        setLocationRelativeTo(null);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    private void startServerButtonListener(ActionEvent e) {

        try {
            if (serverThread == null) {
                int port = availablePort(portInputField.getText());
                portInputField.setText(Integer.toString(port));
                setPort(port);
                serverThread = new ServerThread("ServerThread", getPort());
                serverThread.start();
                SystemProxyUtil.getSystemProxySetting().enableProxy(getPort());
                button.setText("Stop");
            } else {
                SystemProxyUtil.getSystemProxySetting().disableProxy();
                serverThread.stopServer();
                serverThread = null;
                button.setText("Start");
            }
        } catch (IllegalArgumentException ex) {
            showMessageDialog(null, "Enter valid Port number !!!");
            portInputField.setText("8080");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void appendLogToGui(String logMessage, Level eventLevel) {
        try {
            if (textPanel.getDocument().getDefaultRootElement().getElementCount() > logLimit) {
                int end = getLineEndOffset(textPanel, lineLimit);
                replaceRange(textPanel, null, 0, end);
            }

            if (eventLevel == Level.ERROR)
                textPanel.getDocument().insertString(textPanel.getDocument().getLength(), logMessage, ERROR_ATT);
            else if (eventLevel == Level.WARN)
                textPanel.getDocument().insertString(textPanel.getDocument().getLength(), logMessage, WARN_ATT);
            else if (eventLevel == Level.INFO)
                textPanel.getDocument().insertString(textPanel.getDocument().getLength(), logMessage, INFO_ATT);
            else if (eventLevel == Level.DEBUG)
                textPanel.getDocument().insertString(textPanel.getDocument().getLength(), logMessage, DEBUG_ATT);
            else if (eventLevel == Level.TRACE)
                textPanel.getDocument().insertString(textPanel.getDocument().getLength(), logMessage, TRACE_ATT);
            else
                textPanel.getDocument().insertString(textPanel.getDocument().getLength(), logMessage, RESTO_ATT);

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        textPanel.setCaretPosition(textPanel.getDocument().getLength());
    }

    private int getLineCount(JTextPane textPane) {
        return textPane.getDocument().getDefaultRootElement().getElementCount();
    }

    private int getLineEndOffset(JTextPane textPane, int line) throws BadLocationException {
        int lineCount = getLineCount(textPane);
        if (line < 0) {
            throw new BadLocationException("Negative line", -1);
        } else if (line >= lineCount) {
            throw new BadLocationException("No such line", textPane.getDocument().getLength() + 1);
        } else {
            Element map = textPane.getDocument().getDefaultRootElement();
            Element lineElem = map.getElement(line);
            int endOffset = lineElem.getEndOffset();
            // hide the implicit break at the end of the document
            return ((line == lineCount - 1) ? (endOffset - 1) : endOffset);
        }
    }

    /**
     * Código copiado do {@link JTextArea#replaceRange(String, int, int)}<br>
     * <p>
     * Replaces text from the indicated start to end position with the
     * new text specified.  Does nothing if the model is null.  Simply
     * does a delete if the new string is null or empty.<br>
     *
     * @param textPane de onde quero substituir o texto
     * @param str      the text to use as the replacement
     * @param start    the start position &gt;= 0
     * @param end      the end position &gt;= start
     * @throws IllegalArgumentException if part of the range is an invalid position in the model
     */
    private void replaceRange(JTextPane textPane, String str, int start, int end) throws IllegalArgumentException {
        if (end < start) {
            throw new IllegalArgumentException("end before start");
        }
        Document doc = textPane.getDocument();
        if (doc != null) {
            try {
                if (doc instanceof AbstractDocument) {
                    ((AbstractDocument) doc).replace(start, end - start, str, null);
                } else {
                    doc.remove(start, end - start);
                    doc.insertString(start, str, null);
                }
            } catch (BadLocationException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
    }
}
