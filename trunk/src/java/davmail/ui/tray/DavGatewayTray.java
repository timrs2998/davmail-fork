/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.ui.tray;

import davmail.Settings;
import davmail.BundleMessage;
import davmail.exchange.NetworkDownException;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.net.URL;


/**
 * Tray icon handler
 */
public class DavGatewayTray {
    protected static final Logger LOGGER = Logger.getLogger("davmail");

    private DavGatewayTray() {
    }

    static DavGatewayTrayInterface davGatewayTray;

    public static Image getFrameIcon() {
        Image icon = null;
        if (davGatewayTray != null) {
            icon = davGatewayTray.getFrameIcon();
        }
        return icon;
    }

    public static void switchIcon() {
        if (davGatewayTray != null) {
            davGatewayTray.switchIcon();
        }
    }

    public static void resetIcon() {
        if (davGatewayTray != null && isActive()) {
            davGatewayTray.resetIcon();
        }
    }

    public static boolean isActive() {
        return davGatewayTray == null || davGatewayTray.isActive();
    }

    protected static void displayMessage(BundleMessage message, Level level) {
        LOGGER.log(level, message.formatLog());
        if (davGatewayTray != null) {
            davGatewayTray.displayMessage(message.format(), level);
        }
    }

    protected static void displayMessage(BundleMessage message, Exception e, Level level) {
        if (e instanceof NetworkDownException) {
            LOGGER.log(level, BundleMessage.getExceptionLogMessage(message, e));
        } else {
            LOGGER.log(level, BundleMessage.getExceptionLogMessage(message, e), e);
        }
        if (davGatewayTray != null
                && (!(e instanceof NetworkDownException))) {
            davGatewayTray.displayMessage(BundleMessage.getExceptionMessage(message, e), level);
        }
        if (davGatewayTray != null && e instanceof NetworkDownException) {
            davGatewayTray.inactiveIcon();
        }
    }

    public static void debug(BundleMessage message) {
        displayMessage(message, Level.DEBUG);
    }

    public static void info(BundleMessage message) {
        displayMessage(message, Level.INFO);
    }

    public static void warn(BundleMessage message) {
        displayMessage(message, Level.WARN);
    }

    public static void warn(Exception e) {
        displayMessage(null, e, Level.WARN);
    }

    public static void error(BundleMessage message) {
        displayMessage(message, Level.ERROR);
    }

    public static void log(Exception e) {
        // only warn on network down
        if (e instanceof NetworkDownException) {
            warn(e);
        } else {
            error(e);
        }
    }

    public static void error(Exception e) {
        displayMessage(null, e, Level.ERROR);
    }

    public static void debug(BundleMessage message, Exception e) {
        displayMessage(message, e, Level.DEBUG);
    }

    public static void warn(BundleMessage message, Exception e) {
        displayMessage(message, e, Level.WARN);
    }

    public static void error(BundleMessage message, Exception e) {
        displayMessage(message, e, Level.ERROR);
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
                DavGatewayTray.info(new BundleMessage("LOG_SWT_NOT_AVAILABLE"));
            }
            // try java6 tray support
            if (davGatewayTray == null) {
                try {
                    if (SystemTray.isSupported()) {
                        if (isOSX()) {
                            davGatewayTray = new OSXAwtGatewayTray();
                        } else {
                            davGatewayTray = new AwtGatewayTray();
                        }
                        davGatewayTray.init();
                    }
                } catch (NoClassDefFoundError e) {
                    DavGatewayTray.info(new BundleMessage("LOG_SYSTEM_TRAY_NOT_AVAILABLE"));
                }
            }
            if (davGatewayTray == null) {
                if (isOSX()) {
                    // MacOS
                    davGatewayTray = new OSXFrameGatewayTray();
                } else {
                    davGatewayTray = new FrameGatewayTray();
                }
                davGatewayTray.init();
            }
        }
    }

    /**
     * Test if running on OSX
     *
     * @return true on Mac OS X
     */
    protected static boolean isOSX() {
        return System.getProperty("os.name").toLowerCase().startsWith("mac os x");
    }

    /**
     * Load image with current class loader.
     *
     * @param fileName image resource file name
     * @return image
     */
    public static Image loadImage(String fileName) {
        Image result = null;
        try {
            ClassLoader classloader = DavGatewayTray.class.getClassLoader();
            URL imageUrl = classloader.getResource(fileName);
            result = ImageIO.read(imageUrl);
        } catch (IOException e) {
            DavGatewayTray.warn(new BundleMessage("LOG_UNABLE_TO_LOAD_IMAGE"), e);
        }
        return result;
    }
}
