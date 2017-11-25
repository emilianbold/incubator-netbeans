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

package org.netbeans.modules.refactoring.java.ui;

import java.awt.Component;
import java.util.Collections;
import javax.lang.model.element.Modifier;
import javax.swing.*;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.TreePathHandle;
import org.netbeans.api.java.source.ui.ElementIcons;
import org.netbeans.modules.refactoring.java.api.UseSuperTypeRefactoring;
import org.netbeans.modules.refactoring.spi.ui.CustomRefactoringPanel;
import org.openide.util.NbBundle;

/*
 * UseSuperTypePanel.java
 *
 * Created on June 20, 2005
 *
 * @author  Bharath Ravi Kumar
 */

/**
 * The panel for the use super type refactoring
 */
public class UseSuperTypePanel extends JPanel implements CustomRefactoringPanel {
    
    private final UseSuperTypeRefactoring refactoring;
    /**
     * Creates new form UseSuperTypePanel
     * @param refactoring The use super type refactoring that is
     * used by this panel
     * @param className  
     */
    public UseSuperTypePanel(UseSuperTypeRefactoring refactoring, String className) {
        this.refactoring = refactoring;
        initComponents();
        TreePathHandle subType = (TreePathHandle) refactoring.getTypeElement();
        String title = null;
        title = NbBundle.getMessage(UseSuperTypePanel.class, "LBL_UseSyperTypeTitle", className);
        setName(title);
        superTypeList.setCellRenderer(new DefaultListCellRenderer() {
            
            @Override
            public Component getListCellRendererComponent(JList list,
                    Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                
                super.getListCellRendererComponent(list,
                        ((ElementHandle)value).getBinaryName(), index,
                        isSelected, cellHasFocus);
                
                if (value instanceof ElementHandle) {
                    Icon i = ElementIcons.getElementIcon(((ElementHandle) value).getKind(), Collections.singleton(Modifier.PUBLIC));
                    setIcon(i);
                }
                return this;
                
            }
        });
        superTypeList.setModel(new DefaultComboBoxModel(refactoring.getCandidateSuperTypes()));
        superTypeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        superTypeList.setSelectedIndex(0);
    }

    @Override
    public boolean requestFocusInWindow() {
        superTypeList.requestFocusInWindow();
        return true;
    }
    
    @Override
    public void initialize() {
    }
    
    @Override
    public Component getComponent(){
        return this;
    }
    /**
     * Returns the target super type to be used
     * @return The target super type Object
     */
    public ElementHandle getSuperType(){
        return (ElementHandle) superTypeList.getSelectedValue();
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        label = new javax.swing.JLabel();
        listScrollPane = new javax.swing.JScrollPane();
        superTypeList = new javax.swing.JList();

        setMaximumSize(new java.awt.Dimension(600, 500));
        setPreferredSize(new java.awt.Dimension(300, 200));
        setLayout(new java.awt.GridBagLayout());

        label.setLabelFor(superTypeList);
        org.openide.awt.Mnemonics.setLocalizedText(label, org.openide.util.NbBundle.getBundle(UseSuperTypePanel.class).getString("LBL_UseSuperType")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        add(label, gridBagConstraints);
        label.getAccessibleContext().setAccessibleDescription("N/A");

        superTypeList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        listScrollPane.setViewportView(superTypeList);
        superTypeList.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(UseSuperTypePanel.class, "ACSD_SupertypeToUse")); // NOI18N
        superTypeList.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(UseSuperTypePanel.class, "ACSD_SupertypeToUseDescription")); // NOI18N

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(listScrollPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel label;
    private javax.swing.JScrollPane listScrollPane;
    private javax.swing.JList superTypeList;
    // End of variables declaration//GEN-END:variables
    
}
