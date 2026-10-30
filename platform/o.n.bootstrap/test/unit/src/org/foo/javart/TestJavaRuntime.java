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

package org.foo.javart;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.netbeans.SourceRuntime;

/**
 * The reference Java runtime for the engine's contract tests: compiles
 * the module's sources with the system Java compiler (in-process
 * {@code javax.tools}), runs annotation processors from the dependency
 * JARs like an ant build would, and copies non-Java resources to the
 * output. Packaged by SourceModuleTest into a provider module that
 * declares the java token, so the tests exercise the full token/service
 * discovery. The production Java runtime is a module of its own; see
 * platform/source.java.
 */
public class TestJavaRuntime extends SourceRuntime {

    private static final Logger LOG = Logger.getLogger(TestJavaRuntime.class.getName());

    /** Maximal number of compiler diagnostics reported in the exception message. */
    private static final int MAX_ERRORS = 20;

    public @Override String getId() {
        return "java"; // NOI18N
    }

    public @Override String getVersion() {
        return "testrt/" + System.getProperty("java.version"); // NOI18N
    }

    public @Override boolean supportsLazyCompilation() {
        return true;
    }

    public @Override void build(File srcDir, File outputDir, List<File> classpath) throws IOException {
        copyResources(srcDir, outputDir);

        List<File> sourceFiles = new ArrayList<>();
        findJavaFiles(srcDir, sourceFiles);
        if (sourceFiles.isEmpty()) {
            return;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("No system Java compiler is available"); // NOI18N
        }

        String cp = SourceRuntime.joinPaths(classpath);
        List<String> options = new ArrayList<>();
        options.add("-d"); // NOI18N
        options.add(outputDir.getPath());
        options.add("-s"); // NOI18N
        options.add(new File(outputDir.getParentFile(), "gensrc").getPath());
        options.add("-sourcepath"); // NOI18N
        options.add(srcDir.getPath());
        options.add("-classpath"); // NOI18N
        options.add(cp);
        // Processors are shipped inside the dependency JARs, exactly like
        // in an ant build:
        options.add("-processorpath"); // NOI18N
        options.add(cp);
        options.add("-proc:full"); // NOI18N
        options.add("-encoding"); // NOI18N
        options.add("UTF-8"); // NOI18N
        options.add("-g"); // NOI18N
        options.add("-nowarn"); // NOI18N

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        boolean success;
        try {
            success = compiler.getTask(
                    null, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjectsFromFiles(sourceFiles)).call();
        } finally {
            try {
                fileManager.close();
            } catch (IOException ex) {
                LOG.log(Level.FINE, "Cannot close file manager", ex); // NOI18N
            }
        }
        if (!success) {
            throw new IOException("Compilation failed:\n" + formatDiagnostics(diagnostics)); // NOI18N
        }
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            if (count++ == MAX_ERRORS) {
                sb.append("... (further errors suppressed)\n"); // NOI18N
                break;
            }
            if (d.getSource() != null) {
                sb.append(d.getSource().getName()).append(':').append(d.getLineNumber()).append(' '); // NOI18N
            }
            sb.append(d.getMessage(null)).append('\n');
        }
        if (count == 0) {
            sb.append("no detailed diagnostics available"); // NOI18N
        }
        return sb.toString();
    }

    private static void copyResources(File srcDir, File classesDir) throws IOException {
        File[] kids = srcDir.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            if (kid.isDirectory()) {
                copyResources(kid, new File(classesDir, kid.getName()));
            } else if (!kid.getName().endsWith(".java")) { // NOI18N
                File target = new File(classesDir, kid.getName());
                if (!classesDir.isDirectory() && !classesDir.mkdirs()) {
                    throw new IOException("Cannot create " + classesDir); // NOI18N
                }
                copyFile(kid, target);
            }
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream is = new FileInputStream(from);
             OutputStream os = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
    }

    private static void findJavaFiles(File dir, List<File> result) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            if (kid.isDirectory()) {
                findJavaFiles(kid, result);
            } else if (kid.getName().endsWith(".java")) { // NOI18N
                result.add(kid);
            }
        }
    }

}
