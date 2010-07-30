package davmail.ui;

import davmail.DavGateway;
import davmail.Settings;
import davmail.tray.DavGatewayTray;
import org.apache.log4j.Level;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * DavMail settings frame
 */
public class SettingsFrame extends JFrame {
    static final Level[] LOG_LEVELS = {Level.OFF, Level.FATAL, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.ALL};

    protected JTextField urlField;
    protected JTextField popPortField;
    protected JCheckBox popPortCheckBox;
    protected JTextField imapPortField;
    protected JCheckBox imapPortCheckBox;
    protected JTextField smtpPortField;
    protected JCheckBox smtpPortCheckBox;
    protected JTextField caldavPortField;
    protected JCheckBox caldavPortCheckBox;
    protected JTextField ldapPortField;
    protected JCheckBox ldapPortCheckBox;
    protected JTextField keepDelayField;
    protected JTextField sentKeepDelayField;
    protected JTextField caldavPastDelayField;

    JCheckBox enableProxyField;
    JTextField httpProxyField;
    JTextField httpProxyPortField;
    JTextField httpProxyUserField;
    JTextField httpProxyPasswordField;

    JCheckBox allowRemoteField;
    JTextField bindAddressField;
    JTextField certHashField;
    JCheckBox disableUpdateCheck;

    JComboBox rootLoggingLevelField;
    JComboBox davmailLoggingLevelField;
    JComboBox httpclientLoggingLevelField;
    JComboBox wireLoggingLevelField;

    protected void addSettingComponent(JPanel panel, String label, JComponent component) {
        addSettingComponent(panel, label, component, null);
    }

    protected void addSettingComponent(JPanel panel, String label, JComponent component, String toolTipText) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        fieldLabel.setVerticalAlignment(SwingConstants.CENTER);
        panel.add(fieldLabel);
        component.setMaximumSize(component.getPreferredSize());
        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
        innerPanel.add(component);
        panel.add(innerPanel);
        if (toolTipText != null) {
            fieldLabel.setToolTipText(toolTipText);
            component.setToolTipText(toolTipText);
        }
    }

    protected void addPortSettingComponent(JPanel panel, String label, JComponent component, JComponent checkboxComponent, String toolTipText) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        fieldLabel.setVerticalAlignment(SwingConstants.CENTER);
        panel.add(fieldLabel);
        component.setMaximumSize(component.getPreferredSize());
        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
        innerPanel.add(checkboxComponent);
        innerPanel.add(component);
        panel.add(innerPanel);
        if (toolTipText != null) {
            fieldLabel.setToolTipText(toolTipText);
            component.setToolTipText(toolTipText);
        }
    }

    protected JPanel getSettingsPanel() {
        JPanel settingsPanel = new JPanel(new GridLayout(6, 2));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Gateway"));

        urlField = new JTextField(Settings.getProperty("davmail.url"), 17);
        popPortField = new JTextField(Settings.getProperty("davmail.popPort"), 4);
        popPortCheckBox = new JCheckBox();
        popPortCheckBox.setSelected(Settings.getProperty("davmail.popPort") != null && Settings.getProperty("davmail.popPort").length() > 0);
        popPortField.setEnabled(popPortCheckBox.isSelected());
        popPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                popPortField.setEnabled(popPortCheckBox.isSelected());
            }
        });

        imapPortField = new JTextField(Settings.getProperty("davmail.imapPort"), 4);
        imapPortCheckBox = new JCheckBox();
        imapPortCheckBox.setSelected(Settings.getProperty("davmail.imapPort") != null && Settings.getProperty("davmail.imapPort").length() > 0);
        imapPortField.setEnabled(imapPortCheckBox.isSelected());
        imapPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                imapPortField.setEnabled(imapPortCheckBox.isSelected());
            }
        });

        smtpPortField = new JTextField(Settings.getProperty("davmail.smtpPort"), 4);
        smtpPortCheckBox = new JCheckBox();
        smtpPortCheckBox.setSelected(Settings.getProperty("davmail.smtpPort") != null && Settings.getProperty("davmail.smtpPort").length() > 0);
        smtpPortField.setEnabled(smtpPortCheckBox.isSelected());
        smtpPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                smtpPortField.setEnabled(smtpPortCheckBox.isSelected());
            }
        });

        caldavPortField = new JTextField(Settings.getProperty("davmail.caldavPort"), 4);
        caldavPortCheckBox = new JCheckBox();
        caldavPortCheckBox.setSelected(Settings.getProperty("davmail.caldavPort") != null && Settings.getProperty("davmail.caldavPort").length() > 0);
        caldavPortField.setEnabled(caldavPortCheckBox.isSelected());
        caldavPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                caldavPortField.setEnabled(caldavPortCheckBox.isSelected());
            }
        });

        ldapPortField = new JTextField(Settings.getProperty("davmail.ldapPort"), 4);
        ldapPortCheckBox = new JCheckBox();
        ldapPortCheckBox.setSelected(Settings.getProperty("davmail.ldapPort") != null && Settings.getProperty("davmail.ldapPort").length() > 0);
        ldapPortField.setEnabled(ldapPortCheckBox.isSelected());
        ldapPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                ldapPortField.setEnabled(ldapPortCheckBox.isSelected());
            }
        });

        addSettingComponent(settingsPanel, "OWA url: ", urlField, "Base outlook web access URL");
        addPortSettingComponent(settingsPanel, "Local POP port: ", popPortField, popPortCheckBox,
                "Local POP server port to configure in POP client");
        addPortSettingComponent(settingsPanel, "Local IMAP port: ", imapPortField, imapPortCheckBox,
                "Local IMAP server port to configure in IMAP client");
        addPortSettingComponent(settingsPanel, "Local SMTP port: ", smtpPortField, smtpPortCheckBox,
                "Local SMTP server port to configure in POP client");
        addPortSettingComponent(settingsPanel, "Caldav HTTP port: ", caldavPortField, caldavPortCheckBox,
                "Local Caldav server port to configure in Caldav (calendar) client");
        addPortSettingComponent(settingsPanel, "Local LDAP port: ", ldapPortField, ldapPortCheckBox,
                "Local LDAP server port to configure in add directory (addresse book) client");
        return settingsPanel;
    }

    protected JPanel getDelaysPanel() {
        JPanel delaysPanel = new JPanel(new GridLayout(3, 2));
        delaysPanel.setBorder(BorderFactory.createTitledBorder("Delays"));

        keepDelayField = new JTextField(Settings.getProperty("davmail.keepDelay"), 4);
        sentKeepDelayField = new JTextField(Settings.getProperty("davmail.sentKeepDelay"), 4);
        caldavPastDelayField = new JTextField(Settings.getProperty("davmail.caldavPastDelay"), 4);

        addSettingComponent(delaysPanel, "Keep delay: ", keepDelayField,
                "Number of days to keep messages in trash");
        addSettingComponent(delaysPanel, "Sent keep delay: ", sentKeepDelayField,
                "Number of days to keep messages in sent folder");
        addSettingComponent(delaysPanel, "Calendar past events: ", caldavPastDelayField,
                "Get events in the past not older than specified days count, leave empty for no limits");
        return delaysPanel;
    }

    protected JPanel getProxyPanel() {
        JPanel proxyPanel = new JPanel(new GridLayout(5, 2));
        proxyPanel.setBorder(BorderFactory.createTitledBorder("Proxy"));

        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        enableProxyField = new JCheckBox();
        enableProxyField.setSelected(enableProxy);
        httpProxyField = new JTextField(Settings.getProperty("davmail.proxyHost"), 15);
        httpProxyPortField = new JTextField(Settings.getProperty("davmail.proxyPort"), 4);
        httpProxyUserField = new JTextField(Settings.getProperty("davmail.proxyUser"), 10);
        httpProxyPasswordField = new JPasswordField(Settings.getProperty("davmail.proxyPassword"), 10);

        httpProxyField.setEnabled(enableProxy);
        httpProxyPortField.setEnabled(enableProxy);
        httpProxyUserField.setEnabled(enableProxy);
        httpProxyPasswordField.setEnabled(enableProxy);

        enableProxyField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                boolean enableProxy = enableProxyField.isSelected();
                httpProxyField.setEnabled(enableProxy);
                httpProxyPortField.setEnabled(enableProxy);
                httpProxyUserField.setEnabled(enableProxy);
                httpProxyPasswordField.setEnabled(enableProxy);
            }
        });

        addSettingComponent(proxyPanel, "Enable proxy: ", enableProxyField);
        addSettingComponent(proxyPanel, "Proxy server: ", httpProxyField);
        addSettingComponent(proxyPanel, "Proxy port: ", httpProxyPortField);
        addSettingComponent(proxyPanel, "Proxy user: ", httpProxyUserField);
        addSettingComponent(proxyPanel, "Proxy password: ", httpProxyPasswordField);
        return proxyPanel;
    }

    public JPanel getNetworkSettingsPanel() {
        JPanel networkSettingsPanel = new JPanel(new GridLayout(4, 2));
        networkSettingsPanel.setBorder(BorderFactory.createTitledBorder("Network"));

        allowRemoteField = new JCheckBox();
        allowRemoteField.setSelected(Settings.getBooleanProperty("davmail.allowRemote"));

        bindAddressField = new JTextField(Settings.getProperty("davmail.bindAddress"), 15);

        certHashField = new JTextField(Settings.getProperty("davmail.server.certificate.hash"), 15);

        disableUpdateCheck = new JCheckBox();
        disableUpdateCheck.setSelected(Settings.getBooleanProperty("davmail.disableUpdateCheck"));

        addSettingComponent(networkSettingsPanel, "Bind address: ", bindAddressField,
                "Bind only to the specified network address");
        addSettingComponent(networkSettingsPanel, "Allow Remote Connections: ", allowRemoteField,
                "Allow remote connections to the gateway (server mode)");
        addSettingComponent(networkSettingsPanel, "Server certificate hash: ", certHashField,
                "Manually accepted server certificate hash");
        addSettingComponent(networkSettingsPanel, "Disable update check: ", disableUpdateCheck,
                "Disable DavMail check for new version");
        return networkSettingsPanel;
    }

    public JPanel getLoggingSettingsPanel() {
        JPanel loggingSettingsPanel = new JPanel(new GridLayout(4, 2));
        loggingSettingsPanel.setBorder(BorderFactory.createTitledBorder("Logging levels"));

        rootLoggingLevelField = new JComboBox(LOG_LEVELS);
        davmailLoggingLevelField = new JComboBox(LOG_LEVELS);
        httpclientLoggingLevelField = new JComboBox(LOG_LEVELS);
        wireLoggingLevelField = new JComboBox(LOG_LEVELS);

        rootLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("rootLogger"));
        davmailLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("davmail"));
        httpclientLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("org.apache.commons.httpclient"));
        wireLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient.wire"));

        addSettingComponent(loggingSettingsPanel, "Default: ", rootLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, "DavMail: ", davmailLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, "HttpClient: ", httpclientLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, "Wire: ", wireLoggingLevelField);

        return loggingSettingsPanel;
    }

    public void reload() {
        // reload settings in form
        urlField.setText(Settings.getProperty("davmail.url"));
        popPortField.setText(Settings.getProperty("davmail.popPort"));
        popPortCheckBox.setSelected(Settings.getProperty("davmail.popPort") != null && Settings.getProperty("davmail.popPort").length() > 0);
        imapPortField.setText(Settings.getProperty("davmail.imapPort"));
        imapPortCheckBox.setSelected(Settings.getProperty("davmail.imapPort") != null && Settings.getProperty("davmail.imapPort").length() > 0);
        smtpPortField.setText(Settings.getProperty("davmail.smtpPort"));
        smtpPortCheckBox.setSelected(Settings.getProperty("davmail.smtpPort") != null && Settings.getProperty("davmail.smtpPort").length() > 0);
        caldavPortField.setText(Settings.getProperty("davmail.caldavPort"));
        caldavPortCheckBox.setSelected(Settings.getProperty("davmail.caldavPort") != null && Settings.getProperty("davmail.caldavPort").length() > 0);
        ldapPortField.setText(Settings.getProperty("davmail.ldapPort"));
        ldapPortCheckBox.setSelected(Settings.getProperty("davmail.ldapPort") != null && Settings.getProperty("davmail.ldapPort").length() > 0);
        keepDelayField.setText(Settings.getProperty("davmail.keepDelay"));
        sentKeepDelayField.setText(Settings.getProperty("davmail.sentKeepDelay"));
        caldavPastDelayField.setText(Settings.getProperty("davmail.caldavPastDelay"));
        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        enableProxyField.setSelected(enableProxy);
        httpProxyField.setEnabled(enableProxy);
        httpProxyPortField.setEnabled(enableProxy);
        httpProxyUserField.setEnabled(enableProxy);
        httpProxyPasswordField.setEnabled(enableProxy);
        httpProxyField.setText(Settings.getProperty("davmail.proxyHost"));
        httpProxyPortField.setText(Settings.getProperty("davmail.proxyPort"));
        httpProxyUserField.setText(Settings.getProperty("davmail.proxyUser"));
        httpProxyPasswordField.setText(Settings.getProperty("davmail.proxyPassword"));

        bindAddressField.setText(Settings.getProperty("davmail.bindAddress"));
        allowRemoteField.setSelected(Settings.getBooleanProperty(("davmail.allowRemote")));
        certHashField.setText(Settings.getProperty("davmail.server.certificate.hash"));
        disableUpdateCheck.setSelected(Settings.getBooleanProperty(("davmail.disableUpdateCheck")));

        rootLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("rootLogger"));
        davmailLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("davmail"));
        httpclientLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("org.apache.commons.httpclient"));
        wireLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient.wire"));
    }

    public SettingsFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setTitle("DavMail Gateway Settings");
        setIconImage(DavGatewayTray.getFrameIcon());

        JTabbedPane tabbedPane = new JTabbedPane();
        // add help (F1 handler)
        tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F1"),
                "help");
        tabbedPane.getActionMap().put("help", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                DesktopBrowser.browse("http://davmail.sourceforge.net");
            }
        });

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(getSettingsPanel());
        mainPanel.add(getDelaysPanel());
        mainPanel.add(Box.createVerticalGlue());

        tabbedPane.add("Main", mainPanel);

        JPanel advancedPanel = new JPanel();
        advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));

        JPanel proxyPanel = new JPanel();
        proxyPanel.setLayout(new BoxLayout(proxyPanel, BoxLayout.Y_AXIS));
        proxyPanel.add(getProxyPanel());
        // empty panel
        proxyPanel.add(new JPanel());
        tabbedPane.add("Proxy", proxyPanel);

        advancedPanel.add(getNetworkSettingsPanel());
        advancedPanel.add(getLoggingSettingsPanel());

        tabbedPane.add("Advanced", advancedPanel);

        add("Center", tabbedPane);

        JPanel buttonPanel = new JPanel();
        JButton cancel = new JButton("Cancel");
        JButton ok = new JButton("Save");
        JButton help = new JButton("Help");
        ActionListener save = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                // save options
                Settings.setProperty("davmail.url", urlField.getText());
                Settings.setProperty("davmail.popPort", popPortCheckBox.isSelected()?popPortField.getText():"");
                Settings.setProperty("davmail.imapPort", imapPortCheckBox.isSelected()?imapPortField.getText():"");
                Settings.setProperty("davmail.smtpPort", smtpPortCheckBox.isSelected()?smtpPortField.getText():"");
                Settings.setProperty("davmail.caldavPort", caldavPortCheckBox.isSelected()?caldavPortField.getText():"");
                Settings.setProperty("davmail.ldapPort", ldapPortCheckBox.isSelected()?ldapPortField.getText():"");
                Settings.setProperty("davmail.keepDelay", keepDelayField.getText());
                Settings.setProperty("davmail.sentKeepDelay", sentKeepDelayField.getText());
                Settings.setProperty("davmail.caldavPastDelay", caldavPastDelayField.getText());
                Settings.setProperty("davmail.enableProxy", String.valueOf(enableProxyField.isSelected()));
                Settings.setProperty("davmail.proxyHost", httpProxyField.getText());
                Settings.setProperty("davmail.proxyPort", httpProxyPortField.getText());
                Settings.setProperty("davmail.proxyUser", httpProxyUserField.getText());
                Settings.setProperty("davmail.proxyPassword", httpProxyPasswordField.getText());

                Settings.setProperty("davmail.bindAddress", bindAddressField.getText());
                Settings.setProperty("davmail.allowRemote", String.valueOf(allowRemoteField.isSelected()));
                Settings.setProperty("davmail.server.certificate.hash", certHashField.getText());
                Settings.setProperty("davmail.disableUpdateCheck", String.valueOf(disableUpdateCheck.isSelected()));

                Settings.setLoggingLevel("rootLogger", (Level) rootLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("davmail", (Level) davmailLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("org.apache.commons.httpclient", (Level) httpclientLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("httpclient.wire", (Level) wireLoggingLevelField.getSelectedItem());

                dispose();
                Settings.save();
                // restart listeners with new config
                DavGateway.stop();
                DavGateway.start();
            }
        };
        ok.addActionListener(save);

        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                reload();
                dispose();
            }
        });

        help.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                   DesktopBrowser.browse("http://davmail.sourceforge.net");
               }
        });

        buttonPanel.add(ok);
        buttonPanel.add(cancel);
        buttonPanel.add(help);

        add("South", buttonPanel);

        pack();
        //setResizable(false);
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        urlField.requestFocus();
    }
}