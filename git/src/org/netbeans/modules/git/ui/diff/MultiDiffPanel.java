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

package org.netbeans.modules.git.ui.diff;

import javax.swing.UIManager;

/**
 *
 * @author Maros Sandor
 */
class MultiDiffPanel extends javax.swing.JPanel {

    public MultiDiffPanel () {
        initComponents();
        
        if( "Aqua".equals( UIManager.getLookAndFeel().getID() ) ) {             // NOI18N
            setBackground(UIManager.getColor("NbExplorerView.background")); // NOI18N
            controlToolbar.setBackground(UIManager.getColor("NbExplorerView.background")); // NOI18N
            jPanel1.setBackground(UIManager.getColor("NbExplorerView.background")); // NOI18N
            jPanel3.setBackground(UIManager.getColor("NbExplorerView.background")); // NOI18N
            jPanel4.setBackground(UIManager.getColor("NbExplorerView.background")); // NOI18N
            treeSelectionPanel.setBackground(UIManager.getColor("NbExplorerView.background")); //NOI18N
        } 
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btnModeGroup = new javax.swing.ButtonGroup();
        viewTypeGroup = new javax.swing.ButtonGroup();
        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();

        controlToolbar.setFloatable(false);
        controlToolbar.setRollover(true);

        btnModeGroup.add(tgbHeadVsWorking);
        tgbHeadVsWorking.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/head_vs_working.png", false)); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/netbeans/modules/git/ui/diff/Bundle"); // NOI18N
        tgbHeadVsWorking.setToolTipText(bundle.getString("MultiDiffPanel.tgbHeadVsWorking.toolTipText")); // NOI18N
        tgbHeadVsWorking.setFocusable(false);
        controlToolbar.add(tgbHeadVsWorking);

        btnModeGroup.add(tgbHeadVsIndex);
        tgbHeadVsIndex.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/head_vs_index.png", false)); // NOI18N
        tgbHeadVsIndex.setToolTipText(bundle.getString("MultiDiffPanel.tgbHeadVsIndex.toolTipText")); // NOI18N
        tgbHeadVsIndex.setFocusable(false);
        controlToolbar.add(tgbHeadVsIndex);

        btnModeGroup.add(tgbIndexVsWorking);
        tgbIndexVsWorking.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/index_vs_working.png", false)); // NOI18N
        tgbIndexVsWorking.setToolTipText(bundle.getString("MultiDiffPanel.tgbIndexVsWorking.toolTipText")); // NOI18N
        tgbIndexVsWorking.setFocusable(false);
        controlToolbar.add(tgbIndexVsWorking);

        jPanel1.setMaximumSize(new java.awt.Dimension(60, 32767));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        controlToolbar.add(jPanel1);

        viewTypeGroup.add(listButton);
        listButton.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/file_view.png", false)); // NOI18N
        listButton.setToolTipText(bundle.getString("MultiDiffPanel.listButton.toolTipText")); // NOI18N
        listButton.setFocusable(false);
        listButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        listButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        controlToolbar.add(listButton);

        viewTypeGroup.add(treeButton);
        treeButton.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/logical_view.png", false)); // NOI18N
        treeButton.setSelected(true);
        treeButton.setToolTipText(bundle.getString("MultiDiffPanel.treeButton.toolTipText")); // NOI18N
        treeButton.setFocusable(false);
        treeButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        treeButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        controlToolbar.add(treeButton);

        jPanel2.setMaximumSize(new java.awt.Dimension(60, 32767));

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 60, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );

        controlToolbar.add(jPanel2);

        nextButton.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/diff-next.png", false)); // NOI18N
        nextButton.setToolTipText(org.openide.util.NbBundle.getMessage(MultiDiffPanel.class, "MultiDiffPanel.nextButton.toolTipText")); // NOI18N
        nextButton.setFocusable(false);
        nextButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        nextButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        controlToolbar.add(nextButton);

        prevButton.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/diff-prev.png", false)); // NOI18N
        prevButton.setToolTipText(org.openide.util.NbBundle.getMessage(MultiDiffPanel.class, "MultiDiffPanel.prevButton.toolTipText")); // NOI18N
        prevButton.setFocusable(false);
        prevButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        prevButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        controlToolbar.add(prevButton);

        jPanel4.setMaximumSize(new java.awt.Dimension(30, 32767));

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 30, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );

        controlToolbar.add(jPanel4);

        btnRefresh.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/refresh.png", false)); // NOI18N
        btnRefresh.setToolTipText(bundle.getString("MultiDiffPanel.btnRefresh.toolTipText")); // NOI18N
        btnRefresh.setActionCommand("null"); // NOI18N
        btnRefresh.setFocusable(false);
        btnRefresh.setPreferredSize(new java.awt.Dimension(22, 23));
        controlToolbar.add(btnRefresh);

        jPanel3.setMaximumSize(new java.awt.Dimension(20, 32767));
        jPanel3.setOpaque(false);
        controlToolbar.add(jPanel3);

        btnRevert.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/get_clean.png", false)); // NOI18N
        btnRevert.setToolTipText(bundle.getString("MultiDiffPanel.btnRevert.toolTipText")); // NOI18N
        btnRevert.setFocusable(false);
        btnRevert.setPreferredSize(new java.awt.Dimension(22, 25));
        controlToolbar.add(btnRevert);

        btnCommit.setIcon(org.openide.util.ImageUtilities.loadImageIcon("/org/netbeans/modules/git/resources/icons/commit.png", false)); // NOI18N
        btnCommit.setToolTipText(bundle.getString("MultiDiffPanel.btnCommit.toolTipText")); // NOI18N
        btnCommit.setFocusable(false);
        btnCommit.setPreferredSize(new java.awt.Dimension(22, 25));
        controlToolbar.add(btnCommit);

        jLabel1.setLabelFor(cmbDiffTreeFirst);
        jLabel1.setText(org.openide.util.NbBundle.getMessage(MultiDiffPanel.class, "MultiDiffPanel.jLabel1.text")); // NOI18N

        jLabel2.setLabelFor(cmbDiffTreeSecond);
        jLabel2.setText(org.openide.util.NbBundle.getMessage(MultiDiffPanel.class, "MultiDiffPanel.jLabel2.text")); // NOI18N

        javax.swing.GroupLayout treeSelectionPanelLayout = new javax.swing.GroupLayout(treeSelectionPanel);
        treeSelectionPanel.setLayout(treeSelectionPanelLayout);
        treeSelectionPanelLayout.setHorizontalGroup(
            treeSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(treeSelectionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cmbDiffTreeFirst, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cmbDiffTreeSecond, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        treeSelectionPanelLayout.setVerticalGroup(
            treeSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(treeSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(jLabel1)
                .addComponent(cmbDiffTreeFirst, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addComponent(jLabel2)
                .addComponent(cmbDiffTreeSecond, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(splitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 716, Short.MAX_VALUE)
            .addComponent(controlToolbar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(treeSelectionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(controlToolbar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(treeSelectionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(splitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 332, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    final javax.swing.JButton btnCommit = new javax.swing.JButton();
    private javax.swing.ButtonGroup btnModeGroup;
    final javax.swing.JButton btnRefresh = new javax.swing.JButton();
    final javax.swing.JButton btnRevert = new javax.swing.JButton();
    final javax.swing.JComboBox cmbDiffTreeFirst = new javax.swing.JComboBox();
    final javax.swing.JComboBox cmbDiffTreeSecond = new javax.swing.JComboBox();
    final javax.swing.JToolBar controlToolbar = new javax.swing.JToolBar();
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    final javax.swing.JToggleButton listButton = new javax.swing.JToggleButton();
    final javax.swing.JButton nextButton = new javax.swing.JButton();
    final javax.swing.JButton prevButton = new javax.swing.JButton();
    final javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane();
    final javax.swing.JToggleButton tgbHeadVsIndex = new javax.swing.JToggleButton();
    final javax.swing.JToggleButton tgbHeadVsWorking = new javax.swing.JToggleButton();
    final javax.swing.JToggleButton tgbIndexVsWorking = new javax.swing.JToggleButton();
    final javax.swing.JToggleButton treeButton = new javax.swing.JToggleButton();
    final javax.swing.JPanel treeSelectionPanel = new javax.swing.JPanel();
    private javax.swing.ButtonGroup viewTypeGroup;
    // End of variables declaration//GEN-END:variables
}
