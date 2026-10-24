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
import org.openide.modules.Places;

/**
 * Drives on-demand compilation of source-only modules at enablement time.
 * Language-specific work is delegated to a {@link SourceRuntime} (Java by
 * default; other source languages can register their own runtimes through
 * modules). This class is the language-agnostic machinery:
 * <p>
 * A source-only module is a regular module JAR whose manifest carries the
 * {@code OpenIDE-Module-Source} header (value: the runtime id, or
 * {@code true}/{@code java} for the built-in Java runtime) and which
 * contains the module's sources and resources at the JAR root, in the same
 * layout a binary module would have, except that source files appear in
 * place of the compiled class files.
 * <p>
 * The output is cached under {@code var/cache/source-modules/<cnb>/} in
 * the user directory. A stamp file records a digest of the source JAR, of
 * every classpath entry, of the source runtime and of the JDK, so that
 * locally patched modules are detected as stale and recompiled on the
 * next start.
 *
 * @see SourceRuntime
 * @see SourceModule
 * @since org.netbeans.base/1
 */
final class SourceModuleCompiler {

    static final Logger LOG = Logger.getLogger(SourceModuleCompiler.class.getName());

    /** Bump when the compilation setup changes in a way that invalidates old output. */
    private static final int FORMAT_VERSION = 1;

    /** Name of the stamp file inside the module's cache directory. */
    private static final String STAMP_FILE = "stamp.properties";

    /** Attributes copied from JAR to output dir verbatim (excluded from extraction). */
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF"; // NOI18N

    /** Maximal number of compiler diagnostics reported in the exception message. */
    static final int MAX_ERRORS = 20;

    private SourceModuleCompiler() {
    }

    /**
     * Ensures the given source module is compiled and up to date.
     *
     * @param module the module to compile
     * @param runtime the source runtime responsible for the build
     * @param classpath files (JARs or directories) to compile against;
     *        passed through to the runtime
     * @return the directory with the freshly compiled (or cached) output
     * @throws IOException if the build fails; the message contains
     *         diagnostics and becomes the module's problem description
     */
    static File compileIfNeeded(SourceModule module, SourceRuntime runtime, List<File> classpath) throws IOException {
        File jar = module.getJarFile();
        String cnb = module.getCodeNameBase();
        File cacheRoot = Places.getCacheSubdirectory("source-modules/" + cnb.replace('.', '-')); // NOI18N
        File classes = new File(cacheRoot, "classes"); // NOI18N
        File src = new File(cacheRoot, "src"); // NOI18N
        File gensrc = new File(cacheRoot, "gensrc"); // NOI18N

        String stamp = computeStamp(jar, classpath, runtime);
        File stampFile = new File(cacheRoot, STAMP_FILE);
        if (classes.isDirectory() && stampFile.isFile() && stamp.equals(readStamp(stampFile))) {
            LOG.log(Level.FINE, "source module {0} is up to date in {1}", new Object[] {cnb, classes});
            return classes;
        }

        long startTime = System.currentTimeMillis();
        LOG.log(Level.INFO, "compiling source module {0} with runtime {1} ({2})",
                new Object[] {cnb, runtime.getId(), jar});

        // Start from a clean slate - the stamp is only rewritten after a
        // successful build, so a failed run must not leave partial output.
        deleteRecursively(classes);
        deleteRecursively(src);
        deleteRecursively(gensrc);
        if (!src.mkdirs() || !classes.mkdirs() || !gensrc.mkdirs()) {
            throw new IOException("Cannot create compilation directories under " + cacheRoot); // NOI18N
        }

        extractSources(jar, src);
        if (!hasFiles(src)) {
            deleteRecursively(classes);
            throw new IOException("No sources found in source module " + cnb + " (" + jar + ")"); // NOI18N
        }

        try {
            runtime.build(src, classes, classpath);
        } catch (IOException ex) {
            deleteRecursively(classes);
            String detail = ex.getMessage();
            throw new IOException(
                "Compilation of source module " + cnb + " failed" // NOI18N
                + (detail != null && !detail.isEmpty() ? ":\n" + detail : "")
                + "\n[classpath: " + JavaSourceRuntime.joinPaths(classpath) + "]", ex);
        }

        writeStamp(stampFile, stamp);
        LOG.log(Level.INFO, "compiled source module {0} ({1}) in {2} ms",
                new Object[] {cnb, runtime.getId(), System.currentTimeMillis() - startTime});
        return classes;
    }

    /**
     * Digest of everything that can influence the compilation result:
     * the source JAR contents (excluding the manifest, compiled classes of
     * hybrid modules, and build-volatile manifest attributes such as the
     * daily implementation version), the declared classpath entries, the
     * source runtime and its version, and the JDK. Touching or rebuilding
     * a JAR without changing its sources does not trigger a recompilation.
     */
    private static String computeStamp(File jar, List<File> classpath, SourceRuntime runtime) throws IOException {
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
        sb.append("|runtime=").append(runtime.getId()); // NOI18N
        sb.append(':').append(runtime.getVersion());
        sb.append("|crc=").append(crc.getValue()); // NOI18N
        sb.append("|java=").append(System.getProperty("java.version")); // NOI18N
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
     * them, but the compilation output must come from sources alone. The
     * build-time layer hint is build metadata, not module content.
     */
    private static void extractSources(File jar, File targetDir) throws IOException {
        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || MANIFEST_ENTRY.equals(entry.getName())
                        || entry.getName().endsWith(".class") // NOI18N
                        || entry.getName().equals("META-INF/generated-layer.hint")) { // NOI18N
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

    private static boolean hasFiles(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return false;
        }
        for (File kid : kids) {
            if (kid.isDirectory()) {
                if (hasFiles(kid)) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    static void copyFile(File from, File to) throws IOException {
        try (InputStream is = new FileInputStream(from);
             OutputStream os = new FileOutputStream(to)) {
            copyStream(is, os);
        }
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
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
