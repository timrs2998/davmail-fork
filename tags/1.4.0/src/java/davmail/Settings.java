package davmail;

import davmail.tray.DavGatewayTray;

import java.util.Properties;
import java.io.*;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Settings facade
 */
public class Settings {
    protected Settings() {
    }

    private static final Properties SETTINGS = new Properties();
    private static String configFilePath;
    private static boolean isFirstStart;

    public static synchronized void setConfigFilePath(String value) {
        configFilePath = value;
    }

    public static boolean isFirstStart() {
        return isFirstStart;
    }

    public static synchronized void load(InputStream inputStream) throws IOException {
        SETTINGS.load(inputStream);
    }

    public static synchronized void load() {
        FileInputStream fileInputStream = null;
        try {
            if (configFilePath == null) {
                //noinspection AccessOfSystemProperties
                configFilePath = System.getProperty("user.home") + "/.davmail.properties";
            }
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                fileInputStream = new FileInputStream(configFile);
                load(fileInputStream);
            } else {
                isFirstStart = true;

                // first start : set default values, ports above 1024 for linux
                SETTINGS.put("davmail.url", "http://exchangeServer/exchange/");
                SETTINGS.put("davmail.popPort", "1110");
                SETTINGS.put("davmail.smtpPort", "1025");
                SETTINGS.put("davmail.keepDelay", "30");
                SETTINGS.put("davmail.allowRemote", "false");
                SETTINGS.put("davmail.bindAddress", "");
                SETTINGS.put("davmail.enableProxy", "false");
                SETTINGS.put("davmail.proxyHost", "");
                SETTINGS.put("davmail.proxyPort", "");
                SETTINGS.put("davmail.proxyUser", "");
                SETTINGS.put("davmail.proxyPassword", "");
                SETTINGS.put("davmail.server", "false");
                SETTINGS.put("davmail.server.certificate.hash", "");

                // logging
                SETTINGS.put("log4j.rootLogger", "WARN");
                SETTINGS.put("log4j.logger.davmail", "DEBUG");
                SETTINGS.put("log4j.logger.httpclient.wire", "WARN");
                SETTINGS.put("log4j.logger.org.apache.commons.httpclient", "WARN");
                save();
            }
        } catch (IOException e) {
            DavGatewayTray.error("Unable to load settings: ", e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    DavGatewayTray.debug("Error closing configuration file: ", e);
                }
            }
        }
        // update logging levels
        Settings.setLoggingLevel("rootLogger", Settings.getLoggingLevel("rootLogger"));
        Settings.setLoggingLevel("davmail", Settings.getLoggingLevel("davmail"));
        Settings.setLoggingLevel("httpclient.wire", Settings.getLoggingLevel("httpclient.wire"));
        Settings.setLoggingLevel("org.apache.commons.httpclient", Settings.getLoggingLevel("org.apache.commons.httpclient"));

    }

    public static synchronized void save() {
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(configFilePath);
            SETTINGS.store(fileOutputStream, "DavMail settings");
        } catch (IOException e) {
            DavGatewayTray.error("Unable to store settings: ", e);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    DavGatewayTray.debug("Error closing configuration file: ", e);
                }
            }
        }
    }

    public static synchronized String getProperty(String property) {
        return SETTINGS.getProperty(property);
    }

    public static synchronized void setProperty(String property, String value) {
        SETTINGS.setProperty(property, value);
    }

    public static synchronized int getIntProperty(String property) {
        int value = 0;
        try {
            String propertyValue = SETTINGS.getProperty(property);
            if (propertyValue != null && propertyValue.length() > 0) {
                value = Integer.parseInt(propertyValue);
            }
        } catch (NumberFormatException e) {
            DavGatewayTray.error("Invalid setting value in " + property, e);
        }
        return value;
    }

    public static synchronized boolean getBooleanProperty(String property) {
        String propertyValue = SETTINGS.getProperty(property);
        return "true".equals(propertyValue);
    }

    protected static String getLoggingPrefix(String category) {
        String prefix;
        if ("rootLogger".equals(category)) {
            prefix = "log4j.";
        } else {
            prefix = "log4j.logger.";
        }
        return prefix;
    }

    public static synchronized Level getLoggingLevel(String category) {
        String prefix = getLoggingPrefix(category);
        String currentValue = SETTINGS.getProperty(prefix + category);

        if (currentValue != null && currentValue.length() > 0) {
            return Level.toLevel(currentValue);
        } else if ("rootLogger".equals(category)) {
            return Logger.getRootLogger().getLevel();
        } else {
            return Logger.getLogger(category).getLevel();
        }
    }

    public static synchronized void setLoggingLevel(String category, Level level) {
        String prefix = getLoggingPrefix(category);
        SETTINGS.setProperty(prefix + category, level.toString());
        if ("rootLogger".equals(category)) {
            Logger.getRootLogger().setLevel(level);
        } else {
            Logger.getLogger(category).setLevel(level);
        }
    }

    public static synchronized void saveProperty(String property, String value) {
        Settings.load();
        Settings.setProperty(property, value);
        Settings.save();
    }

}