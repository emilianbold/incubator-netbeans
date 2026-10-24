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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Tests the {@link SourceRuntime} SPI: a module written in a (simulated)
 * non-Java language, materialized by a runtime provided by a separate
 * module and discovered through the token/service mechanism.
 */
public class SourceRuntimeTest extends SetupHid {

    public SourceRuntimeTest(String name) {
        super(name);
    }

    private File providerJar;
    private File langJar;

    protected @Override void setUp() throws Exception {
        super.setUp();
        System.clearProperty("dummy.rt.version"); // NOI18N
        providerJar = new File(jars, "dummy-runtime.jar");
        makeRuntimeModule(providerJar, true);
        langJar = new File(jars, "lang-module.jar");
        makeLanguageModule(langJar, true);
    }

    protected @Override void tearDown() throws Exception {
        System.clearProperty("dummy.rt.version"); // NOI18N
    }

    private Module enableLanguageModule(ModuleManager mgr, File jar) throws Exception {
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            Module provider = mgr.create(providerJar, null, false, false, false);
            Module lang = mgr.create(jar, null, false, false, false);
            mgr.enable(new HashSet<Module>(Arrays.asList(provider, lang)));
            return lang;
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
    }

    /**
     * The happy path: a module with OpenIDE-Module-Source: dummy is
     * materialized by the DummyRuntime registered by the provider module.
     */
    public void testNonJavaRuntimeMaterializesModule() throws Exception {
        ModuleManager mgr = newManager();
        Module langMod = enableLanguageModule(mgr, langJar);
        assertTrue(langMod instanceof SourceModule);
        assertTrue(langMod.isEnabled());

        ClassLoader loader = langMod.getClassLoader();
        // "Compiled" output of the dummy language runtime:
        assertNotNull("source served from materialized output",
                loader.getResource("lang/hello.lang")); // NOI18N
        // The runtime was actually invoked and wrote its marker:
        String marker = readResource(loader, "dummy/built.txt"); // NOI18N
        assertEquals("runtime=v1", marker.trim());
    }

    /** A runtime version change invalidates the cache. */
    public void testRuntimeVersionInvalidatesCache() throws Exception {
        ModuleManager mgr = newManager();
        enableLanguageModule(mgr, langJar);

        System.setProperty("dummy.rt.version", "v2"); // NOI18N
        ModuleManager mgr2 = newManager();
        Module langMod = enableLanguageModule(mgr2, langJar);
        String marker = readResource(langMod.getClassLoader(), "dummy/built.txt"); // NOI18N
        assertEquals("runtime=v2", marker.trim());
    }

    /** Using a runtime without requiring its token is rejected with instructions. */
    public void testMissingTokenRequirementIsRejected() throws Exception {
        makeLanguageModule(langJar, false);
        ModuleManager mgr = newManager();
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            Module provider = mgr.create(providerJar, null, false, false, false);
            Module lang = mgr.create(langJar, null, false, false, false);
            try {
                mgr.enable(new HashSet<Module>(Arrays.asList(provider, lang)));
                fail("enablement without the token requirement should fail");
            } catch (InvalidException ex) {
                String msg = findMessage(ex);
                assertTrue("no instructions in: " + msg,
                        msg.contains("does not require the token")); // NOI18N
                assertTrue("no token in: " + msg,
                        msg.contains(SourceRuntime.TOKEN_PREFIX + "dummy")); // NOI18N
            }
            assertFalse(lang.isEnabled());
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
    }

    /** An enabled token provider that registers no runtime yields a helpful error. */
    public void testUnknownRuntimeId() throws Exception {
        // A provider module that provides the token but does NOT register
        // a SourceRuntime service:
        File silentProvider = new File(jars, "silent-runtime.jar"); // NOI18N
        makeRuntimeModule(silentProvider, false);

        ModuleManager mgr = newManager();
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            Module provider = mgr.create(silentProvider, null, false, false, false);
            Module lang = mgr.create(langJar, null, false, false, false);
            try {
                mgr.enable(new HashSet<Module>(Arrays.asList(provider, lang)));
                fail("enablement without a registered runtime should fail");
            } catch (InvalidException ex) {
                String msg = findMessage(ex);
                assertTrue("no runtime hint in: " + msg,
                        msg.contains("No enabled module provides source runtime 'dummy'")); // NOI18N
            }
            assertFalse(lang.isEnabled());
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
    }

    private ModuleManager newManager() {
        return new ModuleManager(new MockModuleInstaller(), new MockEvents());
    }

    private static String findMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            sb.append(t.getMessage());
            t = t.getCause();
        }
        return sb.toString();
    }

    private static String readResource(ClassLoader l, String resource) throws IOException {
        InputStream is = l.getResourceAsStream(resource);
        assertNotNull(resource + " not found", is);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int len;
            while ((len = is.read(b)) != -1) {
                baos.write(b, 0, len);
            }
            return baos.toString("UTF-8"); // NOI18N
        } finally {
            is.close();
        }
    }

    /**
     * Builds the provider module: registers a DummyRuntime (id "dummy")
     * through META-INF/services and provides the matching token.
     */
    private static void makeRuntimeModule(File jar, boolean registerService) throws Exception {
        File src = new File(jar.getParentFile(), "rt-src/org/foo/dummyrt/DummyRuntime.java"); // NOI18N
        writeFile(src,
                "package org.foo.dummyrt;\n" // NOI18N
                + "import java.io.File;\n"
                + "import java.io.FileInputStream;\n"
                + "import java.io.FileOutputStream;\n"
                + "import java.io.IOException;\n"
                + "import java.io.InputStream;\n"
                + "import java.io.OutputStream;\n"
                + "import java.util.List;\n"
                + "import org.netbeans.SourceRuntime;\n"
                + "public class DummyRuntime extends SourceRuntime {\n"
                + "  public @Override String getId() {\n"
                + "    return \"dummy\";\n" // NOI18N
                + "  }\n"
                + "  public @Override String getVersion() {\n"
                + "    String v = System.getProperty(\"dummy.rt.version\");\n" // NOI18N
                + "    return v == null ? \"v1\" : v;\n"
                + "  }\n"
                + "  public @Override void build(File srcDir, File outputDir, List<File> classpath) throws IOException {\n"
                + "    // Simulated language compiler: copy everything verbatim\n"
                + "    // and write a marker proving this code ran and when.\n"
                + "    copyAll(srcDir, outputDir);\n"
                + "    File marker = new File(outputDir, \"dummy/built.txt\");\n" // NOI18N
                + "    marker.getParentFile().mkdirs();\n"
                + "    FileOutputStream os = new FileOutputStream(marker);\n"
                + "    try {\n"
                + "      os.write((\"runtime=\" + getVersion()).getBytes(\"UTF-8\"));\n" // NOI18N
                + "    } finally {\n"
                + "      os.close();\n"
                + "    }\n"
                + "  }\n"
                + "  private static void copyAll(File from, File to) throws IOException {\n"
                + "    File[] kids = from.listFiles();\n"
                + "    if (kids == null) return;\n"
                + "    for (File kid : kids) {\n"
                + "      File t = new File(to, kid.getName());\n"
                + "      if (kid.isDirectory()) {\n"
                + "        t.mkdirs();\n"
                + "        copyAll(kid, t);\n"
                + "      } else {\n"
                + "        t.getParentFile().mkdirs();\n"
                + "        FileInputStream is = new FileInputStream(kid);\n"
                + "        FileOutputStream os = new FileOutputStream(t);\n"
                + "        try {\n"
                + "          byte[] b = new byte[4096];\n"
                + "          int len;\n"
                + "          while ((len = is.read(b)) != -1) os.write(b, 0, len);\n"
                + "        } finally {\n"
                + "          is.close();\n"
                + "          os.close();\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n");
        File classes = new File(jar.getParentFile(), "rt-classes"); // NOI18N
        classes.mkdirs();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        try {
            List<String> options = Arrays.asList(
                    "-d", classes.getPath(),
                    "-classpath", System.getProperty("java.class.path"));
            boolean ok = compiler.getTask(null, fileManager, null, options, null,
                    fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(src))).call();
            assertTrue("compilation of DummyRuntime failed", ok);
        } finally {
            fileManager.close();
        }

        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module", "org.foo.dummyrt"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Public-Packages", "org.foo.dummyrt.*"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Provides", SourceRuntime.TOKEN_PREFIX + "dummy"); // NOI18N
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar), mf)) {
            if (registerService) {
                addJarEntry(jos, "META-INF/services/org.netbeans.SourceRuntime", // NOI18N
                        "org.foo.dummyrt.DummyRuntime\n".getBytes(StandardCharsets.UTF_8)); // NOI18N
            }
            addJarEntry(jos, "org/foo/dummyrt/DummyRuntime.class", // NOI18N
                    readFile(new File(classes, "org/foo/dummyrt/DummyRuntime.class"))); // NOI18N
        }
    }

    /** Builds a source-only module written in the (simulated) dummy language. */
    private static void makeLanguageModule(File jar, boolean requireToken) throws Exception {
        Map<String,String> contents = new LinkedHashMap<>();
        contents.put("lang/hello.lang", "(println \"hello\")\n"); // NOI18N
        contents.put("lang/info.txt", "some resource\n"); // NOI18N
        Map<String,String> manifest = new HashMap<>();
        manifest.put("OpenIDE-Module", "org.foo.langmod"); // NOI18N
        manifest.put("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Module-Dependencies", "org.foo.dummyrt > 1.0"); // NOI18N
        if (requireToken) {
            manifest.put("OpenIDE-Module-Requires", SourceRuntime.TOKEN_PREFIX + "dummy"); // NOI18N
        }
        manifest.put("OpenIDE-Module-Source", "dummy"); // NOI18N
        createJar(jar, contents, manifest);
    }

    private static void writeFile(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] readFile(File f) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = new FileInputStream(f)) {
            byte[] b = new byte[4096];
            int len;
            while ((len = is.read(b)) != -1) {
                baos.write(b, 0, len);
            }
        }
        return baos.toByteArray();
    }

    private static void addJarEntry(JarOutputStream jos, String path, byte[] data) throws IOException {
        JarEntry je = new JarEntry(path);
        je.setSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        je.setCrc(crc.getValue());
        jos.putNextEntry(je);
        jos.write(data);
        jos.closeEntry();
    }

}
