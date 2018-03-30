/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.core.multiview;

import java.awt.event.ActionEvent;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.openide.awt.DynamicMenuContent;
import org.openide.awt.Mnemonics;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Action to show in main menu to split document.
 *
 * @author Th. Oikonomou
 */
@Messages({"CTL_SplitDocumentAction=&Split Document", "CTL_SplitAction=&Split", "MultiViewElement.Spliting.Enabled=true"})
public class SplitAction extends AbstractAction implements Presenter.Menu, Presenter.Popup {

    boolean useSplitName = false;

    public SplitAction() {
	super(Bundle.CTL_SplitDocumentAction());
    }

    public SplitAction(boolean useSplitName) {
	super(Bundle.CTL_SplitDocumentAction());
	this.useSplitName = useSplitName;
    }

    static Action createSplitAction(Map map) {
	if(!isSplitingEnabled()) {
	    return null;
	}
	Object nameObj = map.get("displayName"); //NOI18N
	if (nameObj == null) {
	    return null;
	}
	return new SplitAction(nameObj.toString().equals(Bundle.CTL_SplitAction()));
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
	assert false;
    }

    @Override
    public JMenuItem getMenuPresenter() {
	return getSplitMenuItem();
    }

    @Override
    public JMenuItem getPopupPresenter() {
	return getSplitMenuItem();
    }

    private JMenuItem getSplitMenuItem() {
	if(!isSplitingEnabled()) {
	    return null;
	}
	JMenu menu = new SplitAction.UpdatingMenu();
	String label = useSplitName ? Bundle.CTL_SplitAction() : Bundle.CTL_SplitDocumentAction();
	Mnemonics.setLocalizedText(menu, label);
	return menu;
    }

    static boolean isSplitingEnabled() {
	boolean splitingEnabled = "true".equals(Bundle.MultiViewElement_Spliting_Enabled()); // NOI18N
	return splitingEnabled;
    }

    private static final class UpdatingMenu extends JMenu implements DynamicMenuContent {

	@Override
	public JComponent[] synchMenuPresenters(JComponent[] items) {
	    return getMenuPresenters();
	}

	@Override
	public JComponent[] getMenuPresenters() {
	    assert SwingUtilities.isEventDispatchThread() : "Must be called from AWT";
	    removeAll();
	    final TopComponent tc = WindowManager.getDefault().getRegistry().getActivated();
	    if (tc != null) {
		setEnabled(true);
		if (tc instanceof Splitable && ((Splitable)tc).canSplit()) {

		    JMenuItem item = new JMenuItem(new SplitDocumentVerticallyAction(tc));
		    Mnemonics.setLocalizedText(item, item.getText());
		    add(item);

		    item = new JMenuItem(new SplitDocumentHorizontallyAction(tc));
		    Mnemonics.setLocalizedText(item, item.getText());
		    add(item);

		    item = new JMenuItem(new ClearSplitAction(tc));

		    Mnemonics.setLocalizedText(item, item.getText());
		    add(item);
		} else { // tc is not splitable
		    //No reason to enable action on any TC because now it was enabled even for Welcome page
		    setEnabled(false);
		    /*JRadioButtonMenuItem but = new JRadioButtonMenuItem();
		     Mnemonics.setLocalizedText(but, NbBundle.getMessage(EditorsAction.class, "EditorsAction.source"));
		     but.setSelected(true);
		     add(but);*/
		}
	    } else { // tc == null
		setEnabled(false);
	    }
	    return new JComponent[]{this};
	}
    }
}
