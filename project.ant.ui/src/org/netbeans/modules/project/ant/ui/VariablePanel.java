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

package org.netbeans.modules.project.ant.ui;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.modules.project.ant.ui.VariablesModel.Variable;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.DialogDescriptor;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 *
 */
public class VariablePanel extends javax.swing.JPanel implements DocumentListener {

    private VariablesModel model;
    private DialogDescriptor dd;
    private Variable variableBeingEditted;
    
    /** Creates new form VariableCustomizer */
    public VariablePanel(VariablesModel model, Variable variableBeingEditted) {
        this.model = model;
        this.variableBeingEditted = variableBeingEditted;
        initComponents();
        if (variableBeingEditted != null) {
            nameTextField.setText(variableBeingEditted.getName());
            nameTextField.setEditable(false);
            locationTextField.setText(variableBeingEditted.getValue().getAbsolutePath());
        }
        nameTextField.getDocument().addDocumentListener(this);
        locationTextField.getDocument().addDocumentListener(this);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        nameTextField = new javax.swing.JTextField();
        locationTextField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();
        errorLabel = new javax.swing.JLabel();

        jLabel1.setLabelFor(nameTextField);
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(VariablePanel.class, "VariablePanel.jLabel1.text")); // NOI18N

        jLabel2.setLabelFor(locationTextField);
        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(VariablePanel.class, "VariablePanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(VariablePanel.class, "VariablePanel.browseButton.text")); // NOI18N
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        errorLabel.setForeground(java.awt.Color.red);
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, " ");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel1))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(nameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 242, Short.MAX_VALUE)
                            .addComponent(locationTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 242, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(nameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(browseButton)
                    .addComponent(locationTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(errorLabel))
        );

        jLabel1.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(VariablePanel.class, "ACSD_VariablePanel_NA")); // NOI18N
        jLabel2.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(VariablePanel.class, "ACSD_VariablePanel_NA")); // NOI18N
        nameTextField.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(VariablePanel.class, "ACSD_VariablePanel_NA")); // NOI18N
        locationTextField.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(VariablePanel.class, "ACSD_VariablePanel_NA")); // NOI18N
        browseButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(VariablePanel.class, "ACSD_VariablePanel_NA")); // NOI18N
        errorLabel.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(VariablePanel.class, "ACSD_VariablePanel_NA")); // NOI18N

        getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(VariablePanel.class, "ACSD_VariablePanel_NA")); // NOI18N
        getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(VariablePanel.class, "ACSD_VariablePanel_NA")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
    JFileChooser chooser = new JFileChooser();
    chooser.setFileHidingEnabled(false);
    FileUtil.preventFileChooserSymlinkTraversal(chooser, null);
    chooser.setFileSelectionMode (JFileChooser.DIRECTORIES_ONLY);
    chooser.setMultiSelectionEnabled(false);
    chooser.setDialogTitle(NbBundle.getBundle(VariablePanel.class).getString("MSG_Choose_Folder"));
    if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
        File file = FileUtil.normalizeFile(chooser.getSelectedFile());
        locationTextField.setText(file.getAbsolutePath());
    }
}//GEN-LAST:event_browseButtonActionPerformed

    void setDialogDescriptor(DialogDescriptor dd) {
        assert this.dd == null;
        this.dd = dd;
        checkValidity();
    }
    
    private void checkValidity() {
        String error = null;
        if (nameTextField.getText().length() == 0) {
            error =NbBundle.getBundle(VariablePanel.class).getString("MSG_Invalid_Name");
        } else  if (variableBeingEditted == null && model.find(nameTextField.getText()) != null) {
            error = NbBundle.getBundle(VariablePanel.class).getString("MSG_Variable_Already_Exists");
        } else if (locationTextField.getText().length() == 0 ||
            !getVariableLocation().exists()) {
            error = NbBundle.getBundle(VariablePanel.class).getString("MSG_Invalid_Location");
        } else if (variableBeingEditted == null && !PropertyUtils.isUsablePropertyName(nameTextField.getText())) {
            error = NbBundle.getBundle(VariablePanel.class).getString("MSG_Invalid_Name");
        }
        dd.setValid(error == null);
        errorLabel.setText(error == null ? " " : error); // NOI18N
    }

    public String getVariableName() {
        return nameTextField.getText();
    }
    
    public File getVariableLocation() {
        return FileUtil.normalizeFile(new File(locationTextField.getText()));
    }
    


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JTextField locationTextField;
    private javax.swing.JTextField nameTextField;
    // End of variables declaration//GEN-END:variables

    public void insertUpdate(DocumentEvent arg0) {
        checkValidity();
    }

    public void removeUpdate(DocumentEvent arg0) {
        checkValidity();
    }

    public void changedUpdate(DocumentEvent arg0) {
        checkValidity();
    }

}
