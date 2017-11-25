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
package org.netbeans.modules.bugzilla.issue;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.modules.bugtracking.api.Issue;
import org.netbeans.modules.bugtracking.api.IssueQuickSearch;
import org.netbeans.modules.bugzilla.repository.BugzillaConfiguration;
import org.netbeans.modules.bugzilla.repository.BugzillaRepository;
import org.netbeans.modules.bugzilla.util.BugzillaUtil;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 *
 * @author Jan Stola
 */
public class ResolveIssuePanel extends javax.swing.JPanel {
    private final BugzillaIssue issue;
    private JButton ok = new JButton();

    public ResolveIssuePanel(BugzillaIssue issue) {
        this.issue = issue;
        initComponents();
        // workaround for 0 size container gap caused by invisible duplicatePanel
        ((javax.swing.GroupLayout)getLayout()).setHonorsVisibility(false);

        BugzillaRepository repository = issue.getRepository();
        BugzillaConfiguration bc = repository.getConfiguration();
        if(bc == null || !bc.isValid()) {
            // XXX nice error msg?
            return;
        }
        List<String> resolutions = new LinkedList<String>(bc.getResolutions());
        resolutions.remove("MOVED"); // NOI18N
        duplicatePanel.setVisible(false);
        resolutionCombo.setModel(new DefaultComboBoxModel(resolutions.toArray()));
        org.openide.awt.Mnemonics.setLocalizedText(ok, NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.resolveButton")); // NOI18N
    }

    public String getSelectedResolution() {
        return (String) resolutionCombo.getSelectedItem();
    }

    public String getComment() {
        return textArea.getText();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        resolutionLabel = new javax.swing.JLabel();
        resolutionCombo = new javax.swing.JComboBox();
        commentLabel = new javax.swing.JLabel();
        scrollPane = new javax.swing.JScrollPane();
        textArea = new javax.swing.JTextArea();
        duplicatePanel = new javax.swing.JPanel();
        duplicateLabel = new javax.swing.JLabel();
        duplicateField = new javax.swing.JTextField();
        duplicateButton = new javax.swing.JButton();

        resolutionLabel.setLabelFor(resolutionCombo);
        org.openide.awt.Mnemonics.setLocalizedText(resolutionLabel, org.openide.util.NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.resolutionLabel.text")); // NOI18N

        commentLabel.setLabelFor(textArea);
        org.openide.awt.Mnemonics.setLocalizedText(commentLabel, org.openide.util.NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.commentLabel.text")); // NOI18N

        textArea.setColumns(80);
        textArea.setRows(5);
        scrollPane.setViewportView(textArea);
        textArea.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.textArea.AccessibleContext.accessibleDescription")); // NOI18N

        duplicateLabel.setLabelFor(duplicateField);
        org.openide.awt.Mnemonics.setLocalizedText(duplicateLabel, org.openide.util.NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.duplicateLabel.text")); // NOI18N

        duplicateField.setColumns(15);

        org.openide.awt.Mnemonics.setLocalizedText(duplicateButton, org.openide.util.NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.duplicateButton.text")); // NOI18N
        duplicateButton.setFocusPainted(false);
        duplicateButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        duplicateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                duplicateButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout duplicatePanelLayout = new javax.swing.GroupLayout(duplicatePanel);
        duplicatePanel.setLayout(duplicatePanelLayout);
        duplicatePanelLayout.setHorizontalGroup(
            duplicatePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(duplicatePanelLayout.createSequentialGroup()
                .addComponent(duplicateLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(duplicateField, javax.swing.GroupLayout.PREFERRED_SIZE, 151, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(duplicateButton))
        );
        duplicatePanelLayout.setVerticalGroup(
            duplicatePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(duplicatePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(duplicateLabel)
                .addComponent(duplicateButton)
                .addComponent(duplicateField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        duplicateField.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.duplicateField.AccessibleContext.accessibleDescription")); // NOI18N
        duplicateButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.duplicateButton.AccessibleContext.accessibleDescription")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(resolutionLabel)
                    .addComponent(commentLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(resolutionCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(duplicatePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(scrollPane))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(resolutionLabel)
                        .addComponent(resolutionCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(duplicatePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(commentLabel)
                    .addComponent(scrollPane))
                .addContainerGap())
        );

        resolutionCombo.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(ResolveIssuePanel.class, "ResolveIssuePanel.resolutionCombo.AccessibleContext.accessibleDescription")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    private void duplicateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_duplicateButtonActionPerformed
        Issue i = IssueQuickSearch.selectIssue(
                NbBundle.getMessage(IssuePanel.class, "IssuePanel.duplicateButton.message"), //NOI18N
                BugzillaUtil.getRepository(issue.getRepository()),
                this,
                new HelpCtx("org.netbeans.modules.bugzilla.duplicateChooser")); // NOI18N
        if (i != null) {
            duplicateField.setText(i.getID());
        }
}//GEN-LAST:event_duplicateButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel commentLabel;
    private javax.swing.JButton duplicateButton;
    private javax.swing.JTextField duplicateField;
    private javax.swing.JLabel duplicateLabel;
    private javax.swing.JPanel duplicatePanel;
    private javax.swing.JComboBox resolutionCombo;
    private javax.swing.JLabel resolutionLabel;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JTextArea textArea;
    // End of variables declaration//GEN-END:variables

    private void checkDuplicateId () {
        ok.setEnabled(!BugzillaIssue.RESOLVE_DUPLICATE.equals(resolutionCombo.getSelectedItem()) //NOI18N
                || duplicateField.getText().trim().length() > 0);
    }

    private void resolutionComboSelectionChanged () {
        boolean shown = BugzillaIssue.RESOLVE_DUPLICATE.equals(resolutionCombo.getSelectedItem()); // NOI18N
        duplicatePanel.setVisible(shown);
        checkDuplicateId();
    }

    boolean showDialog() {
        ok.getAccessibleContext().setAccessibleDescription(ok.getText());
        final DialogDescriptor dd = new DialogDescriptor(this, NbBundle.getMessage(ResolveIssuePanel.class, "BugzillaIssueProvider.resolveIssueButton.text"), //NOI18N
                true, new Object[]{ok, DialogDescriptor.CANCEL_OPTION}, ok,
                DialogDescriptor.DEFAULT_ALIGN, new HelpCtx(ResolveIssuePanel.class), null);
        duplicateField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                checkDuplicateId();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                checkDuplicateId();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                checkDuplicateId();
            }
        });
        resolutionCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resolutionComboSelectionChanged();
            }
        });
        resolutionComboSelectionChanged();
        return DialogDisplayer.getDefault().notify(dd) == ok;
    }

    String getDuplicateId() {
        return duplicateField.getText().trim();
    }

}
