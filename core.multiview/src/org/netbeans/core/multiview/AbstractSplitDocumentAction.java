/**
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
import javax.swing.AbstractAction;
import org.netbeans.core.multiview.Splitable;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Christian Lenz (Chrizzly)
 */
public abstract class AbstractSplitDocumentAction extends AbstractAction {

    private int orientation;

    protected AbstractSplitDocumentAction(int orientation) {
        this.orientation = orientation;
    }


    protected void initTopComponent(TopComponent tc) {
        if (tc instanceof Splitable) {
            int split = ((Splitable) tc).getSplitOrientation();
            setEnabled(split == -1 || split != orientation);
        } else {
            setEnabled(false);
        }
    }

    @Override
    public final void actionPerformed(ActionEvent evt) {
        final TopComponent tc = WindowManager.getDefault().getRegistry().getActivated();

        if (tc != null) {
            splitWindow(tc, orientation, -1);
        }
    }


    public static void splitWindow(TopComponent tc, int orientation, int splitLocation) {
        if (tc instanceof Splitable) {
            TopComponent split = ((Splitable) tc).splitComponent(orientation, splitLocation);
            split.open();
            split.requestActive();
            split.invalidate();
            split.revalidate();
            split.repaint();
            split.requestFocusInWindow();
        }
    }

}
