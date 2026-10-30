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

package org.netbeans.nbbuild;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Strips the compiled classes from source-only module JARs (those whose
 * manifest carries OpenIDE-Module-Source), turning hybrid JARs into pure
 * source JARs. A post-build packaging step: the classes exist during the
 * build so that not-yet-converted dependents can compile against them
 * (via the public-package-jars distillation); once the whole build is
 * done they are dead weight - the module system compiles from the
 * sources at runtime and ignores packaged classes entirely.
 * <p>
 * NOTE: trimming rewrites the JARs, changing their size/mtime, which the
 * runtime staleness stamp records - developers with an existing cache
 * recompile once after a trim. (The shipped artifact is fresh anyway.)
 * <p>
 * Usage: {@code java org.netbeans.nbbuild.TrimSourceModules <cluster-dir>...}
 */
public final class TrimSourceModules {

    private TrimSourceModules() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: TrimSourceModules <cluster-dir-or-dist-root>...");
            System.exit(2);
        }
        List<File> moduleDirs = new ArrayList<>();
        for (String dir : args) {
            File f = new File(dir);
            if (new File(f, "modules").isDirectory()) {
                moduleDirs.add(new File(f, "modules"));
            } else if (f.isDirectory()) {
                // a dist root: scan its clusters
                for (File cluster : f.listFiles()) {
                    if (cluster.isDirectory() && new File(cluster, "modules").isDirectory()) {
                        moduleDirs.add(new File(cluster, "modules"));
                    }
                }
            }
        }
        int trimmed = 0;
        long freed = 0;
        for (File modules : moduleDirs) {
            File[] jars = modules.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars == null) {
                continue;
            }
            for (File jar : jars) {
                Manifest mf;
                try (JarFile jf = new JarFile(jar)) {
                    mf = jf.getManifest();
                }
                if (mf == null
                        || mf.getMainAttributes().getValue("OpenIDE-Module-Source") == null) { // NOI18N
                    continue;
                }
                long before = jar.length();
                trim(jar);
                long after = jar.length();
                trimmed++;
                freed += before - after;
                System.out.println("trimmed " + jar + " (" + (before - after) + " bytes freed)"); // NOI18N
            }
        }
        System.out.println("trimmed " + trimmed + " source module JARs, " + freed + " bytes freed"); // NOI18N
    }

    /** Rewrites the JAR without any *.class entries, everything else byte-identical. */
    private static void trim(File jar) throws IOException {
        File tmp = new File(jar.getParentFile(), jar.getName() + ".trimming"); // NOI18N
        tmp.deleteOnExit(); // do not litter modules/ on a crash
        try (JarFile src = new JarFile(jar)) {
            Manifest manifest = src.getManifest();
            try (JarOutputStream out = manifest != null
                    ? new JarOutputStream(new FileOutputStream(tmp), manifest)
                    : new JarOutputStream(new FileOutputStream(tmp))) {
                Enumeration<JarEntry> entries = src.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()
                            || entry.getName().endsWith(".class") // NOI18N
                            || entry.getName().equals("META-INF/MANIFEST.MF")) { // NOI18N
                        continue;
                    }
                    JarEntry copy = new JarEntry(entry.getName());
                    copy.setTime(entry.getTime());
                    out.putNextEntry(copy);
                    try (InputStream is = src.getInputStream(entry)) {
                        copyStream(is, out);
                    }
                    out.closeEntry();
                }
            }
        }
        Files.move(tmp.toPath(), jar.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

}
