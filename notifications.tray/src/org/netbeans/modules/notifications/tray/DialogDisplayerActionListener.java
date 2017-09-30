package org.netbeans.modules.notifications.tray;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

public class DialogDisplayerActionListener implements ActionListener {

    private final String title;
    private final JComponent popupDetails;

    public DialogDisplayerActionListener(final String title, final JComponent popupDetails) {
        this.title = title;
        this.popupDetails = popupDetails;
    }

    private void addListener(Container parent, ActionListener al) {
        if (parent instanceof AbstractButton) {
            ((AbstractButton) parent).addActionListener(al);
        }
        for (Component c : parent.getComponents()) {
            if (c instanceof Container) {
                addListener((Container) c, al);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DialogDescriptor dd = new DialogDescriptor(popupDetails, title, false, NotifyDescriptor.DEFAULT_OPTION, NotifyDescriptor.OK_OPTION, null);
        dd.setMessageType(NotifyDescriptor.INFORMATION_MESSAGE);

        final Dialog d = DialogDisplayer.getDefault().createDialog(dd);
        addListener(popupDetails, (evt) -> {
            d.dispose();
        });
        d.setVisible(true);
    }
}
