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
package org.netbeans.nbbuild;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Build JUnit report from Apache Ant Report XXX
 *
 * @author skygo
 */
public class RatReportTask extends Task {

    private File sourceFile;
    private File root;
    // not nice but to be last
    private static final String OTHERS_AREA = "not cluster";
    private static final String NOT_CLUSTER = "not under cluster";
    private File reportFile;

    /**
     * source file Apache Rat xml report
     *
     * @param sourceFile
     */
    public void setSource(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    /**
     * Folder to put the rat reports in
     *
     * @param report
     */
    public void setReport(File report) {
        this.reportFile = report;
    }

    @Override
    public void execute() throws BuildException {
        root = sourceFile.getParentFile().getParentFile().getParentFile();
        File[] modulesFolder = root.listFiles();
        String repository = "";
        // get repository information from git
        //try {
        List<String> commandAndArgs = new ArrayList<>();
        commandAndArgs.add("git");
        commandAndArgs.add("config");
        commandAndArgs.add("--get");
        commandAndArgs.add("remote.origin.url");
        try {
            Process p = new ProcessBuilder(commandAndArgs).directory(root).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("http")) {
                        repository = line.replace(".git", "");
                    }
                }
            } catch (IOException ex) {
                throw new BuildException("Cannot evaluate git information", ex);
            }
        } catch (IOException ex) {
            throw new BuildException("git process issue", ex);
        }
        // map module and report
        Map<String, ModuleInfo> moduleRATInfo = new TreeMap<>();

        // build map to get cluster and module related from cluster.properties
        Set<String> moduleDB = new HashSet<>();
        Map<String, Set<String>> modulebycluster = new TreeMap<>();
        for (File module : modulesFolder) {
            if (module.isDirectory() && !module.isHidden()) {
                moduleDB.add(module.getName());
                moduleRATInfo.put(module.getName(), new ModuleInfo(module));
            }
        }
        Set<String> clusterList = new TreeSet<>();
        for (String key : getProject().getProperties().keySet()) {
            if (key.startsWith("nb.cluster.")) {
                String simplfiedKey = key.replaceAll("nb.cluster.", "");
                simplfiedKey = simplfiedKey.replaceAll(".dir", "");
                simplfiedKey = simplfiedKey.replaceAll(".depends", "");
                clusterList.add(simplfiedKey);
                modulebycluster.put(simplfiedKey, new HashSet<String>());
            }
        }
        for (String clusterName : clusterList) {
            String property = getProject().getProperty("nb.cluster." + clusterName);
            String[] split = property.split(",");
            for (String amo : split) {
                moduleDB.remove(amo);
                modulebycluster.get(clusterName).add(amo);
            }
        }
        modulebycluster.put(OTHERS_AREA, new HashSet<String>());
        for (String k : moduleDB) {
            modulebycluster.get(OTHERS_AREA).add(k);
        }
        // remaining module sorted in others
        modulebycluster.get(OTHERS_AREA).add(NOT_CLUSTER);
        clusterList.add(OTHERS_AREA);
        moduleRATInfo.put(NOT_CLUSTER, new ModuleInfo(root));
        //read XML
        try {
            DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputStream inputstream = new FileInputStream(sourceFile);
            InputStreamReader reader = new InputStreamReader(inputstream);
            InputSource inputSource = new InputSource(reader);
            Document doc = dBuilder.parse(inputSource); // open xml source
            XPathFactory xpf = XPathFactory.newInstance();

            XPath path = xpf.newXPath();
            Element rootElement = doc.getDocumentElement();

            doPopulateUnapproved(moduleRATInfo, rootElement, path);
            doPopulateApproved(moduleRATInfo, rootElement, path);

        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException ex) {
            throw new BuildException("Cannot parse Rat report ", ex);
        }
        doCheckExternal(moduleRATInfo);

        for (String clusterName : clusterList) {
            // create a report file by cluster
            File file = new File(reportFile, clusterName + ".xml");
            if (!file.exists()) {
                try {
                    reportFile.mkdirs();
                    file.createNewFile();
                } catch (IOException ex) {
                    throw new BuildException("Impossible to create junit rat report for cluster " + clusterName, ex);
                }
            }
            Map<String, String> pseudoTests = new LinkedHashMap<>();
            for (String moduleName : modulebycluster.get(clusterName)) {
                ModuleInfo amoduleInfo = moduleRATInfo.get(moduleName);
                if (amoduleInfo != null) {
                    if (!amoduleInfo.getUnapproved().isEmpty()) {
                        pseudoTests.put(" module " + moduleName + " has " + amoduleInfo.getUnapproved().size() + " unapproved license(s)", "Unapproved license in " + amoduleInfo.getUnapproved().size() + " file(s) " + writeFiles(amoduleInfo.getUnapproved(), repository));
                    }
                    if (!amoduleInfo.getInvalidExternal().isEmpty()) {
                        pseudoTests.put(" module " + moduleName + " has " + amoduleInfo.getInvalidExternal().size() + " suspicious external binaries  file(s)", "List of file " + amoduleInfo.getInvalidExternal().size() + " file(s) " + writeFiles(amoduleInfo.getInvalidExternal(), null));
                    }
                } else {
                    // XXX if a moduleInfo is null folder is not present source base incomplete
                }

            }
            JUnitReportWriter.writeReport(this, "Cluster: " + clusterName, file, pseudoTests);
        }
    }

    private static String writeFiles(Set<String> listFile, String repo) {
        StringBuilder sb = new StringBuilder();
        for (String fileentry : listFile) {
            sb.append("\n");
            if (repo != null) {
                sb.append(repo).append("/tree/master/");
            }
            sb.append(fileentry.replaceAll(" ", "%20"));
        }
        return sb.toString();
    }

    private void doPopulateUnapproved(Map<String, ModuleInfo> moduleRATInfo, Element rootElement, XPath path) throws XPathExpressionException {
        NodeList evaluate = (NodeList) path.evaluate("descendant::resource[license-approval/@name=\"false\"]", rootElement, XPathConstants.NODESET);
        for (int i = 0; i < evaluate.getLength(); i++) {
            String resources = evaluate.item(i).getAttributes().getNamedItem("name").getTextContent().replaceFirst(root.getPath() + File.separator, "");
            String moduleName = getModuleName(resources);
            if (!moduleRATInfo.containsKey(moduleName)) {
                moduleRATInfo.get(NOT_CLUSTER).addUnapproved(resources);
            } else {
                moduleRATInfo.get(moduleName).addUnapproved(resources);
            }
        }
    }

    private void doPopulateApproved(Map<String, ModuleInfo> moduleRATInfo, Element rootElement, XPath path) throws XPathExpressionException {
        NodeList evaluate = (NodeList) path.evaluate("descendant::resource[license-approval/@name=\"true\"]", rootElement, XPathConstants.NODESET);
        for (int i = 0; i < evaluate.getLength(); i++) {
            String resources = evaluate.item(i).getAttributes().getNamedItem("name").getTextContent().replaceFirst(root.getPath() + File.separator, "");
            String moduleName = getModuleName(resources);
            if (!moduleRATInfo.containsKey(moduleName)) {
                moduleRATInfo.get(NOT_CLUSTER).addApproved(resources);
            } else {
                moduleRATInfo.get(moduleName).addApproved(resources);
            }

        }
    }

    private void doCheckExternal(Map<String, ModuleInfo> moduleRATInfo) {
        for (Map.Entry<String, ModuleInfo> minfo : moduleRATInfo.entrySet()) {
            File binaryList = new File(minfo.getValue().getFolder(), File.separator + "external" + File.separator + "binaries-list");
            if (binaryList.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(binaryList))) {
                    String line = br.readLine();
                    while (line != null) {
                        if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                            // comment
                        } else {
                            String[] splitedExternalInfo = line.split(" ");

                            String[] mavenizedExternal = splitedExternalInfo[1].split(":");
                            if (mavenizedExternal.length != 3) {
                                minfo.getValue().addInvalidExternal(splitedExternalInfo[1]);
                            }
                        }
                        line = br.readLine();
                    }
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(RatReportTask.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(RatReportTask.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }
    }

    private String getModuleName(String resource) {
        String moduleName;
        if (!resource.contains(File.separator)) {
            moduleName = NOT_CLUSTER;
        } else {
            moduleName = resource.substring(0, resource.indexOf(File.separator));
        }
        return moduleName;
    }

    class ModuleInfo {
// approved resource list
// very simple string for now.

        private final Set<String> approved = new HashSet<>();
        // unapproved resource list
        private final Set<String> unapproved = new HashSet<>();

        private final Set<String> external = new HashSet<>();
        private final File folder;

        private ModuleInfo(File moduleFolder) {
            this.folder = moduleFolder;
        }

        private File getFolder() {
            return folder;
        }

        private void addApproved(String resources) {
            approved.add(resources);
        }

        private void addInvalidExternal(String resources) {
            external.add(resources);
        }

        private void addUnapproved(String resources) {
            unapproved.add(resources);
        }

        private Set<String> getUnapproved() {
            return unapproved;
        }

        private Set<String> getInvalidExternal() {
            return external;
        }

    }
}
