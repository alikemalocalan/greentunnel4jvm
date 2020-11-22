package com.github.alikemalocalan.greentunnel4jvm;

import com.github.alikemalocalan.greentunnel4jvm.gui.ServerThread;
import com.github.alikemalocalan.greentunnel4jvm.utils.SystemProxyUtil;

import javax.swing.*;
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

        setVisible(true);
        setLocationRelativeTo(null);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public JTextPane getTextPanel() {
        return textPanel;
    }


    private void startServerButtonListener(ActionEvent e) {
        try {
            if (serverThread == null) {
                setPort(availablePort(portInputField.getText()));
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
}
