package davmail.tray;

import davmail.Settings;
import org.apache.log4j.Priority;
import org.apache.log4j.Logger;

import java.awt.*;


/**
 * Tray icon handler
 */
public class DavGatewayTray {
    protected static final Logger LOGGER = Logger.getLogger("davmail");

    protected DavGatewayTray() {
    }

    protected static DavGatewayTrayInterface davGatewayTray;

    public static void switchIcon() {
        if (davGatewayTray != null) {
            davGatewayTray.switchIcon();
        }
    }

    public static void resetIcon() {
        if (davGatewayTray != null) {
            davGatewayTray.resetIcon();
        }
    }

    protected static void displayMessage(String message, Priority priority) {
        LOGGER.log(priority, message);
        if (davGatewayTray != null) {
            davGatewayTray.displayMessage(message, priority);
        }
    }

    protected static void displayMessage(String message, Exception e, Priority priority) {
        LOGGER.log(priority, message, e);
        if (davGatewayTray != null) {
            davGatewayTray.displayMessage(message + " " + e +" "+ e.getMessage(), priority);
        }
    }

    public static void debug(String message) {
        displayMessage(message, Priority.DEBUG);
    }

    public static void info(String message) {
        displayMessage(message, Priority.INFO);
    }

    public static void warn(String message) {
        displayMessage(message, Priority.WARN);
    }

    public static void error(String message) {
        displayMessage(message, Priority.ERROR);
    }

    public static void debug(String message, Exception e) {
        displayMessage(message, e, Priority.DEBUG);
    }

    public static void info(String message, Exception e) {
        displayMessage(message, e, Priority.INFO);
    }

    public static void warn(String message, Exception e) {
        displayMessage(message, e, Priority.WARN);
    }

    public static void error(String message, Exception e) {
        displayMessage(message, e, Priority.ERROR);
    }

    public static void init() {
        if (!Settings.getBooleanProperty("davmail.server")) {
            ClassLoader classloader = DavGatewayTray.class.getClassLoader();
            // first try to load SWT
            try {
                // trigger ClassNotFoundException
                classloader.loadClass("org.eclipse.swt.SWT");
                // SWT available, create tray
                davGatewayTray = new SwtGatewayTray();
                davGatewayTray.init();
            } catch (ClassNotFoundException e) {
                DavGatewayTray.info("SWT not available, fallback to JDK 1.6 system tray support");
            }
            // try java6 tray support
            if (davGatewayTray == null) {
                try {
                    if (SystemTray.isSupported()) {
                        davGatewayTray = new AwtGatewayTray();
                        davGatewayTray.init();
                    }
                } catch (NoClassDefFoundError e) {
                    DavGatewayTray.info("JDK 1.6 needed for system tray support");
                }
            }
            if (davGatewayTray == null) {
                DavGatewayTray.warn("No system tray support found (tried SWT and native java)");
            }
        }
    }
}
