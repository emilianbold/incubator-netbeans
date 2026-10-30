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

package org.netbeans.modules.source.java;

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
 * The Java source runtime: compiles a source-only module's sources with
 * the system Java compiler (in-process {@code javax.tools}), so NetBeans
 * has to be executed on a JDK rather than a bare JRE. Annotation
 * processors shipped inside the dependency module JARs are run exactly
 * like during an ant build ({@code -proc:full}, no extra {@code -A}
 * options), so everything the harness generates at build time -
 * {@code META-INF/services/*} registrations,
 * {@code META-INF/generated-layer.xml}, merged {@code Bundle.properties}
 * files - is regenerated here.
 * <p>
 * This module is an ordinary autoload pulled in by the
 * {@code org.netbeans.modules.source.runtime.java} token that every Java
 * source module requires - the module system itself knows nothing
 * special about Java, see {@link SourceRuntime}.
 *
 * @see SourceRuntime
 */
public class JavaSourceRuntime extends SourceRuntime {

    private static final Logger LOG = Logger.getLogger(JavaSourceRuntime.class.getName());

    /** Maximal number of compiler diagnostics reported in the exception message. */
    private static final int MAX_ERRORS = 20;

    public @Override String getId() {
        return "java"; // NOI18N
    }

    public @Override String getVersion() {
        return System.getProperty("java.version") + '/' + System.getProperty("java.vendor"); // NOI18N
    }

    /**
     * The Java build's output layout is exactly what the module system's
     * lazy loader expects: hybrid JARs ship the build-time generated
     * artifacts (which this runtime regenerates from the same sources)
     * and class loads can be deferred to the first use.
     */
    public @Override boolean supportsLazyCompilation() {
        return true;
    }

    public @Override void build(File srcDir, File outputDir, List<File> classpath) throws IOException {
        // Handwritten resources reach the output directory, like the ant
        // harness' resource copy before jarring:
        copyResources(srcDir, outputDir);

        List<File> sourceFiles = new ArrayList<>();
        findJavaFiles(srcDir, sourceFiles);
        if (sourceFiles.isEmpty()) {
            // Resource-only module (e.g. a kit): nothing to compile.
            return;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException(
                "No system Java compiler is available. " // NOI18N
                + "Run NetBeans with a JDK rather than a JRE, or convert the module to binary form."); // NOI18N
        }

        List<String> options = new ArrayList<>();
        options.add("-d"); // NOI18N
        options.add(outputDir.getPath());
        options.add("-s"); // NOI18N
        options.add(new File(outputDir.getParentFile(), "gensrc").getPath());
        options.add("-sourcepath"); // NOI18N
        options.add(srcDir.getPath());
        options.add("-classpath"); // NOI18N
        options.add(joinPaths(classpath));
        // Note: modules compiling against javac internals (anything with
        // the javacapi wrapper / nb-javac ext jars on its classpath) do not
        // fit the runtime compile: the ant harness builds them with
        // --limit-modules hiding jdk.compiler (SetupLimitModules), but
        // replicating that in-process made javac pathologically slow
        // (hundreds of seconds per module). Such modules must stay binary.
        // Processors (layer generation, @ServiceProvider, @NbBundle.Messages, ...)
        // are shipped inside the dependency JARs themselves, exactly like in
        // an ant build - no -A options are used by the harness either.
        options.add("-processorpath"); // NOI18N
        options.add(joinPaths(classpath));
        if (supportsProcFull()) {
            // JDK 23+ disables annotation processing by default; request it
            // explicitly. The flag exists since JDK 21 (17.0.9 backport).
            options.add("-proc:full"); // NOI18N
        }
        options.add("-encoding"); // NOI18N
        options.add("UTF-8"); // NOI18N
        // Debug info, like the harness' build.compiler.debug/debuglevel
        // (source,lines,vars): without it, debugging a source module
        // shows no local variables.
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

    private static boolean supportsProcFull() {
        Runtime.Version v = Runtime.version();
        return v.feature() > 21
                || (v.feature() == 21)
                || (v.feature() == 17 && v.update() >= 9)
                || (v.feature() == 11 && v.update() >= 21);
    }

    /**
     * Copies resources (everything that is not a Java source) from the
     * source tree into the output directory, mirroring what the ant
     * harness does before jarring a module.
     */
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
