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

package org.netbeans;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.openide.modules.Places;

/**
 * Compiles source-only modules on demand, at module enablement time.
 * <p>
 * A source-only module is a regular module JAR whose manifest carries the
 * {@code OpenIDE-Module-Source: true} header and which contains the module's
 * sources and resources at the JAR root, in the same layout a binary module
 * would have, except that {@code *.java} files appear in place of the
 * compiled {@code *.class} files.
 * <p>
 * Compilation is performed in-process using the platform compiler
 * ({@link ToolProvider#getSystemJavaCompiler()}), which means NetBeans has to
 * be executed by a JDK rather than a bare JRE. Annotation processors shipped
 * inside the dependency module JARs are run exactly like during an ant build
 * ({@code -proc:full}, no extra {@code -A} options), so everything the harness
 * generates at build time - {@code META-INF/services/*} registrations,
 * {@code META-INF/generated-layer.xml}, merged {@code Bundle.properties}
 * files - is regenerated here, byte-for-byte equivalent in structure.
 * <p>
 * The output is cached under {@code var/cache/source-modules/<cnb>/} in the
 * user directory. A stamp file records a digest of the source JAR, of every
 * classpath entry and of the running JDK, so that locally patched modules are
 * detected as stale and recompiled on the next start.
 *
 * @since org.netbeans.base/1
 */
final class SourceModuleCompiler {

    private static final Logger LOG = Logger.getLogger(SourceModuleCompiler.class.getName());

    /** Bump when the compilation setup changes in a way that invalidates old output. */
    private static final int FORMAT_VERSION = 1;

    /** Name of the stamp file inside the module's cache directory. */
    private static final String STAMP_FILE = "stamp.properties";

    /** Attributes copied from JAR to output dir verbatim (excluded from extraction). */
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF"; // NOI18N

    /** Maximal number of compiler diagnostics reported in the exception message. */
    private static final int MAX_ERRORS = 20;

    private SourceModuleCompiler() {
    }

    /**
     * Ensures the given source module is compiled and up to date.
     *
     * @param module the module to compile
     * @param classpath files (JARs or directories) to compile against;
     *        used as both {@code -classpath} and {@code -processorpath}
     * @return the directory with the freshly compiled (or cached) classes
     * @throws IOException if compilation fails; the message contains the
     *         compiler diagnostics
     */
    static File compileIfNeeded(SourceModule module, List<File> classpath) throws IOException {
        File jar = module.getJarFile();
        String cnb = module.getCodeNameBase();
        File cacheRoot = Places.getCacheSubdirectory("source-modules/" + cnb.replace('.', '-')); // NOI18N
        File classes = new File(cacheRoot, "classes"); // NOI18N
        File src = new File(cacheRoot, "src"); // NOI18N
        File gensrc = new File(cacheRoot, "gensrc"); // NOI18N

        String stamp = computeStamp(jar, classpath);
        File stampFile = new File(cacheRoot, STAMP_FILE);
        if (classes.isDirectory() && stampFile.isFile() && stamp.equals(readStamp(stampFile))) {
            LOG.log(Level.FINE, "source module {0} is up to date in {1}", new Object[] {cnb, classes});
            return classes;
        }

        long startTime = System.currentTimeMillis();
        LOG.log(Level.INFO, "compiling source module {0} ({1})", new Object[] {cnb, jar});

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException(
                "Cannot compile source module " + cnb + ": no system Java compiler is available. " + // NOI18N
                "Run NetBeans with a JDK rather than a JRE, or convert the module to binary form."); // NOI18N
        }

        // Start from a clean slate - the stamp is only rewritten after a
        // successful compilation, so a failed run must not leave half output.
        deleteRecursively(classes);
        deleteRecursively(src);
        deleteRecursively(gensrc);
        if (!src.mkdirs() || !classes.mkdirs() || !gensrc.mkdirs()) {
            throw new IOException("Cannot create compilation directories under " + cacheRoot); // NOI18N
        }

        extractSources(jar, src);
        copyResources(src, classes);

        List<File> sourceFiles = new ArrayList<>();
        findJavaFiles(src, sourceFiles);
        if (sourceFiles.isEmpty()) {
            deleteRecursively(classes);
            throw new IOException("No Java sources found in source module " + cnb + " (" + jar + ")"); // NOI18N
        }

        List<String> options = new ArrayList<>();
        options.add("-d"); // NOI18N
        options.add(classes.getPath());
        options.add("-s"); // NOI18N
        options.add(gensrc.getPath());
        options.add("-sourcepath"); // NOI18N
        options.add(src.getPath());
        options.add("-classpath"); // NOI18N
        options.add(joinPaths(classpath));
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
                LOG.log(Level.FINE, "Cannot close file manager", ex);
            }
        }
        if (!success) {
            deleteRecursively(classes);
            throw new IOException("Compilation of source module " + cnb + " failed:\n" // NOI18N
                    + formatDiagnostics(diagnostics));
        }

        writeStamp(stampFile, stamp);
        LOG.log(Level.INFO, "compiled source module {0} ({1} source files) in {2} ms",
                new Object[] {cnb, sourceFiles.size(), System.currentTimeMillis() - startTime});
        return classes;
    }

    /**
     * Digest of everything that can influence the compilation result:
     * the source JAR contents (excluding the manifest, compiled classes of
     * hybrid modules, and build-volatile manifest attributes such as the
     * daily implementation version), the declared classpath entries, and the
     * JDK. Touching or rebuilding a JAR without changing its sources does
     * not trigger a recompilation.
     */
    private static String computeStamp(File jar, List<File> classpath) throws IOException {
        CRC32 crc = new CRC32();
        try (JarFile jarFile = new JarFile(jar)) {
            byte[] buffer = new byte[8192];
            for (JarEntry entry : Collections.list(jarFile.entries())) {
                if (entry.isDirectory() || MANIFEST_ENTRY.equals(entry.getName())
                        || entry.getName().endsWith(".class")) { // NOI18N
                    continue;
                }
                crc.update(entry.getName().getBytes(StandardCharsets.UTF_8));
                try (InputStream is = jarFile.getInputStream(entry)) {
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        crc.update(buffer, 0, len);
                    }
                }
            }
            // Manifest attributes, minus volatile build stamps.
            Manifest mf = jarFile.getManifest();
            if (mf != null) {
                for (Map.Entry<Object,Object> attr : mf.getMainAttributes().entrySet()) {
                    String name = String.valueOf(attr.getKey());
                    if ("OpenIDE-Module-Implementation-Version".equals(name) // NOI18N
                            || "OpenIDE-Module-Build-Version".equals(name)) { // NOI18N
                        continue;
                    }
                    crc.update(name.getBytes(StandardCharsets.UTF_8));
                    crc.update(String.valueOf(attr.getValue()).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("format=").append(FORMAT_VERSION); // NOI18N
        sb.append("|crc=").append(crc.getValue()); // NOI18N
        sb.append("|java=").append(System.getProperty("java.version")); // NOI18N
        sb.append('/').append(System.getProperty("java.vendor")); // NOI18N
        for (File f : classpath) {
            sb.append("|cp=").append(f.getPath()); // NOI18N
            sb.append(':').append(f.length());
            sb.append(':').append(f.lastModified());
        }
        return sb.toString();
    }

    private static String readStamp(File stampFile) throws IOException {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(stampFile)) {
            props.load(is);
        }
        return props.getProperty("stamp", ""); // NOI18N
    }

    private static void writeStamp(File stampFile, String stamp) throws IOException {
        Properties props = new Properties();
        props.setProperty("stamp", stamp); // NOI18N
        try (OutputStream os = new FileOutputStream(stampFile)) {
            props.store(os, "Source-only module compilation stamp. Delete to force recompilation."); // NOI18N
        }
    }

    /**
     * Extracts all JAR entries except the manifest into the given directory.
     * Compiled classes are skipped too: hybrid modules (transition form,
     * packaged with classes for their binary build-time dependents) carry
     * them, but the runtime compilation output must come from sources alone.
     */
    private static void extractSources(File jar, File targetDir) throws IOException {
        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || MANIFEST_ENTRY.equals(entry.getName())
                        || entry.getName().endsWith(".class")) { // NOI18N
                    continue;
                }
                File target = new File(targetDir, entry.getName());
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Cannot create " + parent); // NOI18N
                }
                // Simple sanity check - entry names with ../ are not expected
                // in module JARs but do not let them escape the cache dir.
                if (!target.toPath().normalize().startsWith(targetDir.toPath().normalize())) {
                    throw new IOException("Suspicious JAR entry name: " + entry.getName()); // NOI18N
                }
                try (InputStream is = jarFile.getInputStream(entry);
                     OutputStream os = new FileOutputStream(target)) {
                    copyStream(is, os);
                }
            }
        }
    }

    /**
     * Copies resources (everything that is not a Java source) from the
     * extracted source tree into the classes output directory, mirroring
     * what the ant harness does before jarring a module.
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
                if (!classesDir.isDirectory() && !classesDir.mkdirs()) {
                    throw new IOException("Cannot create " + classesDir); // NOI18N
                }
                copyFile(kid, new File(classesDir, kid.getName()));
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

    private static String joinPaths(List<File> files) {
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            if (sb.length() > 0) {
                sb.append(File.pathSeparatorChar);
            }
            sb.append(f.getPath());
        }
        return sb.toString();
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream is = new FileInputStream(from);
             OutputStream os = new FileOutputStream(to)) {
            copyStream(is, os);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        File[] kids = file.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                deleteRecursively(kid);
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Cannot delete " + file); // NOI18N
        }
    }

    /** Converts a {@code jar:file:...!/META-INF/MANIFEST.MF} URL to the JAR file. */
    static File jarUrlToFile(URL url) {
        String s = url.toString();
        if (!s.startsWith("jar:file:")) { // NOI18N
            return null;
        }
        int bang = s.indexOf("!/"); // NOI18N
        try {
            URI uri = new URI(bang == -1 ? s.substring(4) : s.substring(4, bang));
            return new File(uri);
        } catch (URISyntaxException ex) {
            LOG.log(Level.WARNING, "Cannot convert {0} to a file", url);
            return null;
        }
    }

}
