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
    private File interpProviderJar;
    private File langInterpJar;

    protected @Override void setUp() throws Exception {
        super.setUp();
        System.clearProperty("dummy.rt.version"); // NOI18N
        providerJar = new File(jars, "dummy-runtime.jar");
        makeRuntimeModule(providerJar, true);
        langJar = new File(jars, "lang-module.jar");
        makeLanguageModule(langJar, true);
        interpProviderJar = new File(jars, "interp-runtime.jar");
        makeInterpreterRuntimeModule(interpProviderJar);
        langInterpJar = new File(jars, "lang-interp-module.jar");
        makeInterpretedModule(langInterpJar);
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

    /**
     * Interpreter mode: the module is plain source code served directly
     * from the JAR - classes defined on demand by the runtime, resources
     * (including service registrations) read from source form - and
     * nothing is ever materialized to disk.
     */
    public void testInterpreterRuntimeServesModuleFromSourceForm() throws Exception {
        ModuleManager mgr = newManager();
        Module langMod;
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            Module provider = mgr.create(interpProviderJar, null, false, false, false);
            Module lang = mgr.create(langInterpJar, null, false, false, false);
            mgr.enable(new HashSet<Module>(Arrays.asList(provider, lang)));
            langMod = lang;
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
        assertTrue(langMod instanceof SourceModule);
        assertTrue(langMod.isEnabled());

        ClassLoader loader = langMod.getClassLoader();
        // The class is defined in memory by the interpreting runtime:
        Class<?> c = loader.loadClass("lang.Hello"); // NOI18N
        assertEquals("interp-v1", c.getField("ID").get(null));
        Runnable r = (Runnable) c.getDeclaredConstructor().newInstance();
        r.run();

        // Handwritten resources are served straight from the source JAR:
        String services = readResource(loader, "META-INF/services/java.lang.Runnable"); // NOI18N
        assertEquals("lang.Hello", services.trim()); // NOI18N
        assertNotNull(loader.getResource("lang/Hello.lang")); // NOI18N
    }

    /** Interpreter mode must not create any cache directory. */
    public void testInterpreterModeWritesNoCache() throws Exception {
        ModuleManager mgr = newManager();
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            Module provider = mgr.create(interpProviderJar, null, false, false, false);
            Module lang = mgr.create(langInterpJar, null, false, false, false);
            mgr.enable(new HashSet<Module>(Arrays.asList(provider, lang)));
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
        File cache = new File(org.openide.modules.Places.getCacheDirectory(), "source-modules/org-foo-langmodi"); // NOI18N
        assertFalse("interpreter mode must not materialize anything: " + cache, cache.isDirectory());
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

    /**
     * Builds the interpreter-mode provider module: registers a
     * DummyInterpreterRuntime that serves classes straight from the
     * source JAR, defining them in memory on demand.
     */
    private static void makeInterpreterRuntimeModule(File jar) throws Exception {
        File src = new File(jar.getParentFile(), "irt-src/org/foo/dummyi/DummyInterpreterRuntime.java"); // NOI18N
        writeFile(src,
                "package org.foo.dummyi;\n" // NOI18N
                + "import java.io.ByteArrayOutputStream;\n"
                + "import java.io.IOException;\n"
                + "import java.io.InputStream;\n"
                + "import java.io.OutputStream;\n"
                + "import java.net.URL;\n"
                + "import java.net.URLClassLoader;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.util.Arrays;\n"
                + "import java.util.List;\n"
                + "import java.io.File;\n"
                + "import javax.tools.FileObject;\n"
                + "import javax.tools.ForwardingJavaFileManager;\n"
                + "import javax.tools.JavaCompiler;\n"
                + "import javax.tools.JavaFileManager;\n"
                + "import javax.tools.JavaFileObject;\n"
                + "import javax.tools.SimpleJavaFileObject;\n"
                + "import javax.tools.StandardJavaFileManager;\n"
                + "import javax.tools.ToolProvider;\n"
                + "import org.netbeans.Module;\n"
                + "import org.netbeans.SourceRuntime;\n"
                + "public class DummyInterpreterRuntime extends SourceRuntime {\n"
                + "  public @Override String getId() {\n"
                + "    return \"dummyi\";\n" // NOI18N
                + "  }\n"
                + "  public @Override String getVersion() {\n"
                + "    return \"v1\";\n"
                + "  }\n"
                + "  public @Override ClassLoader createSourceLoader(Module m, List<File> classpath, ClassLoader[] parents) throws IOException {\n"
                + "    // Interpret: resources come from the source JAR itself;\n"
                + "    // classes are defined in memory on demand. No disk output.\n"
                + "    return new InterpretingLoader(m.getJarFile().toURI().toURL(),\n"
                + "        parents.length == 0 ? null : parents[0]);\n"
                + "  }\n"
                + "  static final class InterpretingLoader extends URLClassLoader {\n"
                + "    InterpretingLoader(URL jar, ClassLoader parent) {\n"
                + "      super(new URL[] {jar}, parent);\n"
                + "    }\n"
                + "    protected @Override Class<?> findClass(String name) throws ClassNotFoundException {\n"
                + "      byte[] bytes = interpret(name);\n"
                + "      if (bytes == null) {\n"
                + "        return super.findClass(name);\n"
                + "      }\n"
                + "      return defineClass(name, bytes, 0, bytes.length);\n"
                + "    }\n"
                + "    /** The \"language\": source of class X is stored as X.lang in the JAR. */\n"
                + "    private byte[] interpret(String className) {\n"
                + "      String resource = className.replace('.', '/') + \".lang\"; // NOI18N\n"
                + "      InputStream is = getResourceAsStream(resource);\n"
                + "      if (is == null) return null;\n"
                + "      try {\n"
                + "        String source = new String(is.readAllBytes(), StandardCharsets.UTF_8);\n"
                + "        return compileInMemory(className, source);\n"
                + "      } catch (IOException ex) {\n"
                + "        return null;\n"
                + "      } finally {\n"
                + "        try { is.close(); } catch (IOException ex) {}\n"
                + "      }\n"
                + "    }\n"
                + "    private static byte[] compileInMemory(String className, String source) {\n"
                + "      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();\n"
                + "      if (compiler == null) return null;\n"
                + "      JavaFileObject src = new SimpleJavaFileObject(\n"
                + "          java.net.URI.create(\"string:///\" + className.replace('.', '/') + \".java\"), // NOI18N\n"
                + "          JavaFileObject.Kind.SOURCE) {\n"
                + "        public @Override CharSequence getCharContent(boolean ignore) {\n"
                + "          return source;\n"
                + "        }\n"
                + "      };\n"
                + "      ByteArrayOutputStream out = new ByteArrayOutputStream();\n"
                + "      JavaFileObject bin = new SimpleJavaFileObject(\n"
                + "          java.net.URI.create(\"mem:///\" + className.replace('.', '/') + \".class\"), // NOI18N\n"
                + "          JavaFileObject.Kind.CLASS) {\n"
                + "        public @Override OutputStream openOutputStream() {\n"
                + "          return out;\n"
                + "        }\n"
                + "      };\n"
                + "      StandardJavaFileManager std = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);\n"
                + "      JavaFileManager fm = new ForwardingJavaFileManager<JavaFileManager>(std) {\n"
                + "        public @Override JavaFileObject getJavaFileForOutput(\n"
                + "                Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {\n"
                + "          return bin;\n"
                + "        }\n"
                + "      };\n"
                + "      if (!compiler.getTask(null, fm, null, null, null, Arrays.asList(src)).call()) return null;\n"
                + "      return out.toByteArray();\n"
                + "    }\n"
                + "  }\n"
                + "}\n");
        File classes = new File(jar.getParentFile(), "irt-classes"); // NOI18N
        classes.mkdirs();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        try {
            List<String> options = Arrays.asList(
                    "-d", classes.getPath(),
                    "-classpath", System.getProperty("java.class.path"));
            boolean ok = compiler.getTask(null, fileManager, null, options, null,
                    fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(src))).call();
            assertTrue("compilation of DummyInterpreterRuntime failed", ok);
        } finally {
            fileManager.close();
        }

        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module", "org.foo.dummyi"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Public-Packages", "org.foo.dummyi.*"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Provides", SourceRuntime.TOKEN_PREFIX + "dummyi"); // NOI18N
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar), mf)) {
            addJarEntry(jos, "META-INF/services/org.netbeans.SourceRuntime", // NOI18N
                    "org.foo.dummyi.DummyInterpreterRuntime\n".getBytes(StandardCharsets.UTF_8)); // NOI18N
            jarUpDir(jos, classes, "");
        }
    }

    private static void jarUpDir(JarOutputStream jos, File dir, String prefix) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            String path = prefix + f.getName();
            if (f.isDirectory()) {
                jarUpDir(jos, f, path + "/");
            } else {
                addJarEntry(jos, path, readFile(f));
            }
        }
    }

    /** Builds an interpreted (interpreter-mode) source-only module. */
    private static void makeInterpretedModule(File jar) throws Exception {
        Map<String,String> contents = new LinkedHashMap<>();
        contents.put("lang/Hello.lang", // NOI18N
                "package lang;\n" // NOI18N
                + "public class Hello implements Runnable {\n"
                + "  public static final String ID = \"interp-v1\";\n" // NOI18N
                + "  public @Override void run() {\n"
                + "    if (!\"ok\".equals(\"ok\")) throw new IllegalStateException();\n" // NOI18N
                + "  }\n"
                + "}\n");
        contents.put("META-INF/services/java.lang.Runnable", "lang.Hello\n"); // NOI18N
        Map<String,String> manifest = new HashMap<>();
        manifest.put("OpenIDE-Module", "org.foo.langmodi"); // NOI18N
        manifest.put("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Module-Dependencies", "org.foo.dummyi > 1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Requires", SourceRuntime.TOKEN_PREFIX + "dummyi"); // NOI18N
        manifest.put("OpenIDE-Module-Source", "dummyi"); // NOI18N
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
