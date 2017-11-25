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

package org.netbeans.modules.debugger.jpda.visual.models;

import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Action;
import javax.swing.JComponent;

import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.modules.debugger.jpda.ui.breakpoints.ControllerProvider;
import org.netbeans.spi.viewmodel.NodeActionsProvider;
import org.netbeans.spi.viewmodel.UnknownTypeException;
import org.netbeans.spi.viewmodel.Models;

import org.netbeans.modules.debugger.jpda.visual.breakpoints.ComponentBreakpointPanel;
import org.netbeans.modules.debugger.jpda.visual.breakpoints.ComponentBreakpoint;
import org.netbeans.spi.debugger.DebuggerServiceRegistration;
import org.netbeans.spi.debugger.ui.Controller;
import org.netbeans.spi.viewmodel.NodeActionsProviderFilter;

import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;


/**
 * @author Martin Entlicher
 */
@DebuggerServiceRegistration(path="BreakpointsView", types=NodeActionsProviderFilter.class)
public class ComponentBreakpointsActionsProvider implements NodeActionsProviderFilter {
    
    private static final Action CUSTOMIZE_ACTION = Models.createAction (
        loc("CTL_Breakpoint_Customize_Label"), // NOI18N
        new Models.ActionPerformer () {
        @Override
            public boolean isEnabled (Object node) {
                return true;
            }
        @Override
            public void perform (Object[] nodes) {
                customize ((Breakpoint) nodes [0]);
            }
        },
        Models.MULTISELECTION_TYPE_EXACTLY_ONE
    );
        
    private static String loc(String key) {
        return NbBundle.getBundle(ComponentBreakpointsActionsProvider.class).getString(key);
    }

    @Override
    public Action[] getActions (NodeActionsProvider original, Object node) 
    throws UnknownTypeException {
        if (!(node instanceof ComponentBreakpoint)) 
            return original.getActions (node);
        
        Action[] oas = original.getActions (node);
        Action[] as = new Action [oas.length + 1];
        System.arraycopy (oas, 0, as, 0, oas.length);
        as [as.length - 1] = CUSTOMIZE_ACTION;
        return as;
    }
    
    @Override
    public void performDefaultAction (NodeActionsProvider original, Object node) throws UnknownTypeException {
        if (node instanceof ComponentBreakpoint) 
            customize ((Breakpoint) node);
        else
            original.performDefaultAction (node);
    }
    
    public static JComponent getCustomizerComponent(Breakpoint b) {
        JComponent c = null;
        if (b instanceof ComponentBreakpoint) {
            c = new ComponentBreakpointPanel((ComponentBreakpoint) b);
        }

        c.getAccessibleContext().setAccessibleDescription(
                NbBundle.getMessage(ComponentBreakpointsActionsProvider.class, "ACSD_ComponentBreakpoint_Customizer_Dialog")); // NOI18N
         return c;
    }

    public static void customize (Breakpoint b) {
        JComponent c = getCustomizerComponent(b);
        HelpCtx helpCtx = HelpCtx.findHelp (c);
        if (helpCtx == null) {
            helpCtx = new HelpCtx ("debug.add.breakpoint");  // NOI18N
        }
        Controller cc;
        if (c instanceof ControllerProvider) {
            cc = ((ControllerProvider) c).getController();
        } else {
            cc = (Controller) c;
        }

        final Controller[] cPtr = new Controller[] { cc };
        final DialogDescriptor[] descriptorPtr = new DialogDescriptor[1];
        final Dialog[] dialogPtr = new Dialog[1];
        ActionListener buttonsActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                if (descriptorPtr[0].getValue() == DialogDescriptor.OK_OPTION) {
                    boolean ok = cPtr[0].ok();
                    if (ok) {
                        dialogPtr[0].setVisible(false);
                    }
                } else {
                    dialogPtr[0].setVisible(false);
                }
            }
        };
        DialogDescriptor descriptor = new DialogDescriptor (
            c,
            NbBundle.getMessage (
                ComponentBreakpointsActionsProvider.class,
                "CTL_ComponentBreakpoint_Customizer_Title" // NOI18N
            ),
            true,
            DialogDescriptor.OK_CANCEL_OPTION,
            DialogDescriptor.OK_OPTION,
            DialogDescriptor.DEFAULT_ALIGN,
            helpCtx,
            buttonsActionListener
        );
        descriptor.setClosingOptions(new Object[] {});
        Dialog d = DialogDisplayer.getDefault ().createDialog (descriptor);
        d.pack ();
        descriptorPtr[0] = descriptor;
        dialogPtr[0] = d;
        d.setVisible (true);
    }
    
}
