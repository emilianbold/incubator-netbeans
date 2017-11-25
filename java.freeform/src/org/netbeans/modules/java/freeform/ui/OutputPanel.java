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

package org.netbeans.modules.java.freeform.ui;

import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import org.netbeans.modules.ant.freeform.spi.support.Util;
import org.netbeans.modules.java.freeform.JavaProjectGenerator;
import org.openide.filesystems.FileUtil;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 *
 * @author  David Konecny
 */
public class OutputPanel extends javax.swing.JPanel implements HelpCtx.Provider {

    private DefaultListModel listModel;
    private File lastChosenFile = null;
    private boolean isSeparateClasspath = true;
    private List<ProjectModel.CompilationUnitKey> compUnitsKeys;
    private boolean ignoreEvent;
    private ProjectModel model;
    
    public OutputPanel() {
        initComponents();
        jTextArea1.setBackground(getBackground());
        listModel = new DefaultListModel();
        output.setModel(listModel);
        // XXX: for now only single selection
        output.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        javadoc.getDocument().addDocumentListener(new javax.swing.event.DocumentListener () {
            public void insertUpdate(DocumentEvent e) {
                update ();
            }
    
            public void removeUpdate(DocumentEvent e) {
                update ();
            }
    
            public void changedUpdate(DocumentEvent e) {
                update ();
            }
        });        
        jTextArea1.setDisabledTextColor(jLabel2.getForeground());
    }    
    
    private void update() {
        int index = sourceFolder.getSelectedIndex();
        assert index >= 0;
        ProjectModel.CompilationUnitKey key = compUnitsKeys.get(index);
        JavaProjectGenerator.JavaCompilationUnit cu = model.getCompilationUnit(key, model.isTestSourceFolder(index));
        updateCompilationUnitJavadoc(cu);
    }


    public HelpCtx getHelpCtx() {
        return new HelpCtx( OutputPanel.class );
    }
    
    private void updateControls() {
        sourceFolder.removeAllItems();
        compUnitsKeys = model.createCompilationUnitKeys();
        isSeparateClasspath = !ProjectModel.isSingleCompilationUnit(compUnitsKeys);
        List<String> names = ClasspathPanel.createComboContent(compUnitsKeys, model.getEvaluator(), model.getNBProjectFolder());
        for (String nm : names) {
            sourceFolder.addItem(nm);
        }
        if (names.size() > 0) {
            ignoreEvent = true;
            sourceFolder.setSelectedIndex(0);
            ignoreEvent = false;
        }
        
        loadOutput();        
        
        // enable/disable "Separate Classpath" checkbox
        boolean sepClasspath = model.canHaveSeparateClasspath();
        jLabel2.setEnabled(sepClasspath && isSeparateClasspath);
        sourceFolder.setEnabled(sepClasspath && isSeparateClasspath);
        
        // disable ouput panel and Add Output button if there is 
        // no compilation unit ot be configured
        addOutput.setEnabled(compUnitsKeys.size() > 0);        
        output.setEnabled(compUnitsKeys.size() > 0);
        javadoc.setEnabled(compUnitsKeys.size() > 0);
        javadocBrowse.setEnabled(compUnitsKeys.size() > 0);
        updateButtons();
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jLabel3 = new javax.swing.JLabel();
        addOutput = new javax.swing.JButton();
        removeOutput = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        output = new javax.swing.JList();
        jPanel1 = new javax.swing.JPanel();
        sourceFolder = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        jTextArea1 = new javax.swing.JTextArea();
        jPanel2 = new javax.swing.JPanel();
        javadoc = new javax.swing.JTextField();
        javadocBrowse = new javax.swing.JButton();
        javadocLabel = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jTextArea2 = new javax.swing.JTextArea();
        jPanel4 = new javax.swing.JPanel();
        jTextArea3 = new javax.swing.JTextArea();

        setPreferredSize(new java.awt.Dimension(275, 202));
        setLayout(new java.awt.GridBagLayout());

        jLabel3.setLabelFor(output);
        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(OutputPanel.class, "LBL_OutputPanel_jLabel3")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(11, 0, 0, 0);
        add(jLabel3, gridBagConstraints);
        jLabel3.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSD_OutputPanel_jLabel3")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(addOutput, org.openide.util.NbBundle.getMessage(OutputPanel.class, "BTN_OutputPanel_addOutput")); // NOI18N
        addOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addOutputActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 0);
        add(addOutput, gridBagConstraints);
        addOutput.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSD_OutputPanel_addOutput")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(removeOutput, org.openide.util.NbBundle.getMessage(OutputPanel.class, "BTN_OutputPanel_removeOutput")); // NOI18N
        removeOutput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeOutputActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 0);
        add(removeOutput, gridBagConstraints);
        removeOutput.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSD_OutputPanel_removeOutput")); // NOI18N

        output.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                outputValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(output);
        output.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSN_OutputPanel_output")); // NOI18N
        output.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSD_OutputPanel_output")); // NOI18N

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridheight = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(jScrollPane1, gridBagConstraints);
        jScrollPane1.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSD_OutputPanel_jScrollPanel1")); // NOI18N

        jPanel1.setLayout(new java.awt.GridBagLayout());

        sourceFolder.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                sourceFolderItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 0);
        jPanel1.add(sourceFolder, gridBagConstraints);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/netbeans/modules/java/freeform/ui/Bundle"); // NOI18N
        sourceFolder.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_OutputPanel_sourceFolder")); // NOI18N

        jLabel2.setLabelFor(sourceFolder);
        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(OutputPanel.class, "LBL_OutputPanel_jLabel1")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        jPanel1.add(jLabel2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        add(jPanel1, gridBagConstraints);

        jTextArea1.setEditable(false);
        jTextArea1.setLineWrap(true);
        jTextArea1.setText(org.openide.util.NbBundle.getMessage(OutputPanel.class, "MSG_OutputPanel_jTextArea1")); // NOI18N
        jTextArea1.setWrapStyleWord(true);
        jTextArea1.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 11, 0);
        add(jTextArea1, gridBagConstraints);
        jTextArea1.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSN_OutputPanel_jTextArea1")); // NOI18N
        jTextArea1.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSD_OutputPanel_jTextArea1")); // NOI18N

        jPanel2.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 6);
        jPanel2.add(javadoc, gridBagConstraints);
        javadoc.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_OutputPanel_javadoc")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(javadocBrowse, org.openide.util.NbBundle.getMessage(OutputPanel.class, "BTN_OutputPanel_browseJavadoc")); // NOI18N
        javadocBrowse.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                javadocBrowseActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        jPanel2.add(javadocBrowse, gridBagConstraints);
        javadocBrowse.getAccessibleContext().setAccessibleDescription(bundle.getString("AD_OutputPanel_javadocBrowse")); // NOI18N

        javadocLabel.setLabelFor(javadoc);
        org.openide.awt.Mnemonics.setLocalizedText(javadocLabel, org.openide.util.NbBundle.getMessage(OutputPanel.class, "LBL_OutputPanel_JavadocLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 6);
        jPanel2.add(javadocLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(18, 0, 0, 0);
        add(jPanel2, gridBagConstraints);

        jPanel3.setBorder(javax.swing.BorderFactory.createLineBorder(javax.swing.UIManager.getDefaults().getColor("Label.disabledForeground")));
        jPanel3.setLayout(new java.awt.GridBagLayout());

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/netbeans/modules/java/freeform/resources/alert_32.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 8, 8, 0);
        jPanel3.add(jLabel1, gridBagConstraints);

        jTextArea2.setBackground(javax.swing.UIManager.getDefaults().getColor("Panel.background"));
        jTextArea2.setEditable(false);
        jTextArea2.setLineWrap(true);
        jTextArea2.setText(org.openide.util.NbBundle.getMessage(OutputPanel.class, "Freeform_Warning_Message")); // NOI18N
        jTextArea2.setWrapStyleWord(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 10, 4, 4);
        jPanel3.add(jTextArea2, gridBagConstraints);
        jTextArea2.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSN_Freeform_Warning_Message" )); // NOI18N
        jTextArea2.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSD_Freeform_Warning_Message" )); // NOI18N

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
        add(jPanel3, gridBagConstraints);

        jPanel4.setLayout(new java.awt.GridBagLayout());

        jTextArea3.setBackground(javax.swing.UIManager.getDefaults().getColor("Panel.background"));
        jTextArea3.setEditable(false);
        jTextArea3.setLineWrap(true);
        jTextArea3.setText(org.openide.util.NbBundle.getMessage(OutputPanel.class, "Warning_Need_Output_Message")); // NOI18N
        jTextArea3.setWrapStyleWord(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        jPanel4.add(jTextArea3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
        add(jPanel4, gridBagConstraints);

        getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSN_OutputPanel")); // NOI18N
        getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(OutputPanel.class, "ACSD_OutputPanel")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    private void javadocBrowseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_javadocBrowseActionPerformed
        JFileChooser chooser = new JFileChooser();
        FileUtil.preventFileChooserSymlinkTraversal(chooser, null);
        chooser.setFileSelectionMode (JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(false);
        if (lastChosenFile != null) {
            chooser.setSelectedFile(lastChosenFile);
        } else if (javadoc.getText().length() > 0) {
            chooser.setSelectedFile(new File(javadoc.getText()));
        } else {
            File files[] = model.getBaseFolder().listFiles();
            if (files != null && files.length > 0) {
                chooser.setSelectedFile(files[0]);
            } else {
                chooser.setSelectedFile(model.getBaseFolder());
            }
        }
        chooser.setDialogTitle(NbBundle.getMessage(OutputPanel.class, "LBL_Browse_Javadoc")); // NOI18N
        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
            File file = chooser.getSelectedFile();
            file = FileUtil.normalizeFile(file);
            javadoc.setText(file.getAbsolutePath());
            lastChosenFile = file;
        }
    }//GEN-LAST:event_javadocBrowseActionPerformed

    private void outputValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_outputValueChanged
        updateButtons();
    }//GEN-LAST:event_outputValueChanged

    private void sourceFolderItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sourceFolderItemStateChanged
        if (ignoreEvent) {
            return;
        }
        if (evt.getStateChange() == ItemEvent.DESELECTED) {
            int index = findIndex(evt.getItem());
            // if index == -1 then item was removed and will not be saved
            if (index != -1) {
                saveOutput(index);
            }
        } else {
            loadOutput();
        }
    }//GEN-LAST:event_sourceFolderItemStateChanged

    private int findIndex(Object o) {
        for (int i=0; i<sourceFolder.getModel().getSize(); i++) {
            if (sourceFolder.getModel().getElementAt(i).equals(o)) {
                return i;
            }
        }
        return -1;
    }
    
    /** Source package combo is changing - take output from the listbox and
     * store it in compilaiton unit identified by the index.*/
    private void saveOutput(int index) {
        ProjectModel.CompilationUnitKey key = compUnitsKeys.get(index);
        JavaProjectGenerator.JavaCompilationUnit cu = model.getCompilationUnit(key, model.isTestSourceFolder(index));
        updateCompilationUnitOutput(cu);
        updateCompilationUnitJavadoc(cu);
    }

    /** Source package has changes - find current source package and read its compilation unit and
     * update classpath listbox with it.*/
    private void loadOutput() {
        int index;
        if (isSeparateClasspath) {
            index = sourceFolder.getSelectedIndex();
            if (index == -1) {
                return;
            }
        } else {
            index = 0;
        }
        //Issue #163334
        if (index < 0 || index >= compUnitsKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Index: %d Size: %d IsSeparateClassPath: %b",
                    index,
                    compUnitsKeys.size(),
                    isSeparateClasspath));
        }
        ProjectModel.CompilationUnitKey key = compUnitsKeys.get(index);
        JavaProjectGenerator.JavaCompilationUnit cu = model.getCompilationUnit(key, model.isTestSourceFolder(index));
        updateJListOutput(cu.output);
        updateJavadocField(cu.javadoc);
    }
    
    private void updateJavadocField(List<String> jd) {
        String value = "";
        boolean enabled = true;
        if (jd != null) {
            if (jd.size() > 1) {
                value = getListAsString(jd);
                enabled = false;
            } else if (jd.size() == 1) {
                File f = Util.resolveFile(model.getEvaluator(), model.getNBProjectFolder(), jd.get(0));
                if (f != null) {
                    value = f.getAbsolutePath();
                }
            }
        }
        javadoc.setEnabled(enabled);
        javadocBrowse.setEnabled(enabled);
        javadoc.setText(value);
    }
    
    private String getListAsString(List<String> list) {
        assert list != null;
        StringBuffer sb = new StringBuffer();
        for (String s : list) {
            File f = Util.resolveFile(model.getEvaluator(), model.getNBProjectFolder(), s);
            if (f != null) {
                if (sb.length()>0) {
                    sb.append(", "); // NOI18N
                }
                sb.append(f.getAbsolutePath());
            }            
        }
        return sb.toString();
    }
    
    private void updateCompilationUnitJavadoc(JavaProjectGenerator.JavaCompilationUnit cu) {
        if (javadoc.isEnabled()) {
            if (javadoc.getText().length() > 0) {
                cu.javadoc = new ArrayList<String>();
                for (String part : javadoc.getText().split(",")) {
                    File f = FileUtil.normalizeFile(new File(part.trim()));
                    String path = Util.relativizeLocation(model.getBaseFolder(), model.getNBProjectFolder(), f);
                    cu.javadoc.add (path);
                }                
            } else {
                cu.javadoc = null;
            }
        }
    }

    /** Read content of list box and update compilation unit's output.*/
    private void updateCompilationUnitOutput(JavaProjectGenerator.JavaCompilationUnit cu) {
        if (output.getModel().getSize() == 0) {
            cu.output = null;
            return;
        }
        List<String> l = new ArrayList<String>();
        for (int i=0; i<output.getModel().getSize(); i++) {
            File f = new File((String)output.getModel().getElementAt(i));
            String path = Util.relativizeLocation(model.getBaseFolder(), model.getNBProjectFolder(), f);
            l.add(path);
        }
        cu.output = l;
    }

    /** Update panel's listbox with given output list. */
    private void updateJListOutput(List<String> l) {
        listModel.removeAllElements();
        if (l != null) {
            for (String out : l) {
                File f = Util.resolveFile(model.getEvaluator(), model.getNBProjectFolder(), out);
                if (f != null) {
                    listModel.addElement(f.getAbsolutePath());
                }
            }
        }
        updateButtons();
    }
    
    private void updateButtons() {
        removeOutput.setEnabled(output.isEnabled() && listModel.getSize() > 0 && output.getSelectedIndex() != -1);
    }
    
    private void removeOutputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeOutputActionPerformed
        int i = output.getSelectedIndex();
        if (i != -1) {
            listModel.remove(i);
        }
        applyChanges();
        updateButtons();        
    }//GEN-LAST:event_removeOutputActionPerformed

    private void addOutputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addOutputActionPerformed
        JFileChooser chooser = new JFileChooser();
        FileUtil.preventFileChooserSymlinkTraversal(chooser, null);
        chooser.setFileSelectionMode (JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        if (lastChosenFile != null) {
            chooser.setSelectedFile(lastChosenFile);
        } else {
            File files[] = model.getBaseFolder().listFiles();
            if (files != null && files.length > 0) {
                chooser.setSelectedFile(files[0]);
            } else {
                chooser.setSelectedFile(model.getBaseFolder());
            }
        }
        chooser.setDialogTitle(NbBundle.getMessage(OutputPanel.class, "LBL_Browse_Output")); // NOI18N
        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
            for (File file : chooser.getSelectedFiles()) {
                file = FileUtil.normalizeFile(file);
                listModel.addElement(file.getAbsolutePath());
                lastChosenFile = file;
            }
            applyChanges();
            updateButtons();
        }
    }//GEN-LAST:event_addOutputActionPerformed

    private void applyChanges() {
        if (isSeparateClasspath) {
            if (sourceFolder.getSelectedIndex() != -1) {
                saveOutput(sourceFolder.getSelectedIndex());
            }
        } else {
            saveOutput(0);
        }
    }
    
    void setModel(ProjectModel model) {
        this.model = model;
        updateControls();
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent arg0) {
                updateControls();
            }
        });
    }

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addOutput;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextArea jTextArea2;
    private javax.swing.JTextArea jTextArea3;
    private javax.swing.JTextField javadoc;
    private javax.swing.JButton javadocBrowse;
    private javax.swing.JLabel javadocLabel;
    private javax.swing.JList output;
    private javax.swing.JButton removeOutput;
    private javax.swing.JComboBox sourceFolder;
    // End of variables declaration//GEN-END:variables
    
}
