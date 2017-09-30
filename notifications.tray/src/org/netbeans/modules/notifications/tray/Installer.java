package org.netbeans.modules.notifications.tray;

import java.awt.SystemTray;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.ImageIcon;
import org.openide.awt.NotificationDisplayer;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.windows.WindowManager;

public class Installer extends ModuleInstall {

    // flags to force enable/disable
    private final static String DISABLE_FLAG = "org.netbeans.modules.notifications.tray.disable"; //NOI18N
    private final static String ENABLE_FLAG = "org.netbeans.modules.notifications.tray.enable"; //NOI18N

    final static URL APPLICATION_ICON_URL;

    static {
        URL url = null;
        try {
            url = new URL("nbresloc:/org/netbeans/core/startup/frame.gif"); //NOI18N
        } catch (MalformedURLException ex) {
        }
        APPLICATION_ICON_URL = url;
    }

    @NbBundle.Messages({
        "welcome.title=Welcome to Apache NetBeans (incubating)",
        "welcome.message=The best IDE"
    })
    @Override
    public void restored() {
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            NotificationDisplayer.getDefault().notify(Bundle.welcome_title(), new ImageIcon(APPLICATION_ICON_URL), Bundle.welcome_message(), null);
        });
    }

    @Override
    public void validate() throws IllegalStateException {
        if (Boolean.getBoolean(DISABLE_FLAG) || !SystemTray.isSupported()) {
            throw new IllegalStateException();
        }
        if (Boolean.getBoolean(ENABLE_FLAG)) {
            return;
        }

        //disabled on macOS, will have to be forcefully enabled
        if (Utilities.isMac()) {
            throw new IllegalStateException("System Tray notifications disabled on macOS since they don't use the macOS Notification Center"); //NOI18N
        }
    }

}
