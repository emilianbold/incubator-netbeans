package org.netbeans.modules.notifications.tray;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import org.openide.awt.Notification;
import org.openide.awt.NotificationDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = NotificationDisplayer.class, position = 50)
public class SystemTrayNotificationDisplayer extends NotificationDisplayer {

    private final TrayIcon trayIcon;
    private ActionListener previousActionListener;
    private String command;

    public SystemTrayNotificationDisplayer() {
        this(ensureTrayIcon(SystemTray.getSystemTray()));
    }

    public SystemTrayNotificationDisplayer(TrayIcon trayIcon) {
        this.trayIcon = trayIcon;
        trayIcon.addActionListener((ActionEvent e) -> {
            if (e.getActionCommand() != null && e.getActionCommand().equals(command) && previousActionListener != null) {
                previousActionListener.actionPerformed(e);
            }
        });
    }

    private static TrayIcon ensureTrayIcon(SystemTray systemTray) {
        TrayIcon[] icons = systemTray.getTrayIcons();
        if (icons.length > 0) {
            return icons[0];
        }

        Dimension trayIconSize = systemTray.getTrayIconSize();
        int dim = Math.max(trayIconSize.width, trayIconSize.height);
        final String file;
        if (dim <= 16) {
            file = "frame.gif"; //NOI18N
        } else if (dim <= 32) {
            file = "frame32.gif"; //NOI18N
        } else {
            file = "frame48.gif"; //NOI18N
        }

        try {
            Image image = new ImageIcon(new URL("nbresloc:/org/netbeans/core/startup/" + file)).getImage(); //NOI18N
            TrayIcon icon = new TrayIcon(image);
            icon.setImageAutoSize(true);

            systemTray.add(icon);

            return icon;
        } catch (AWTException | MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        }

        throw new IllegalStateException();
    }

    private TrayIcon.MessageType forPriority(NotificationDisplayer.Priority p) {
        switch (p) {
            case HIGH:
            case NORMAL:
            case LOW:
                return TrayIcon.MessageType.INFO;
            case SILENT:
            default:
                return TrayIcon.MessageType.NONE;
        }
    }

    @Override
    public Notification notify(String title, Icon icon, String details, ActionListener al, NotificationDisplayer.Priority p) {
        trayIcon.displayMessage(title, details, forPriority(p));

        if (al != null) {
            command = UUID.randomUUID().toString();
            trayIcon.setActionCommand(command);
        }
        previousActionListener = al;

        return new Notification() {
            @Override
            public void clear() {
                //TODO:
            }
        };
    }

    @NbBundle.Messages({
        "customUI_message=Custom UI used, click to see more."
    })
    @Override
    public Notification notify(final String title, Icon icon, JComponent balloonDetails, final JComponent popupDetails, NotificationDisplayer.Priority p) {
        return notify(title, icon, Bundle.customUI_message(), new DialogDisplayerActionListener(title, popupDetails), p);
    }

}
