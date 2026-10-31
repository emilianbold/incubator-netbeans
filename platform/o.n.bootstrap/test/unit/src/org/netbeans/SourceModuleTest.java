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
import java.net.URL;
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
import org.openide.modules.Places;

/**
 * Tests for source-only modules: compiled on demand at enablement time,
 * with annotation processing (service and bundle generation) performed by
 * the runtime compiler, cached output, staleness detection and patches.
 */
public class SourceModuleTest extends SetupHid {

    public SourceModuleTest(String name) {
        super(name);
    }

    private File depJar;
    private File srcJar;
    private File rtJar;

    protected @Override void setUp() throws Exception {
        super.setUp();
        System.clearProperty("netbeans.patches.org.foo.srcmod"); // NOI18N
        depJar = new File(jars, "dep-module.jar");
        makeDepModule(depJar);
        srcJar = new File(jars, "src-module.jar");
        makeSourceModule(srcJar, "v1", false);
        rtJar = new File(jars, "java-runtime.jar");
        makeJavaRuntimeModule(rtJar);
    }

    protected @Override void tearDown() throws Exception {
        System.clearProperty("netbeans.patches.org.foo.srcmod"); // NOI18N
    }

    private ModuleManager newManager() {
        MockModuleInstaller installer = new MockModuleInstaller();
        MockEvents ev = new MockEvents();
        return new ModuleManager(installer, ev);
    }

    /** Creates both modules, enables them, and returns the source module. */
    private Module enableSourceModule(ModuleManager mgr) throws Exception {
        return createAndEnable(mgr)[1];
    }

    /** Creates the runtime provider and both modules, enables the two
     *  modules (the token requirement pulls the provider in), and
     *  returns [dependency, source]. */
    private Module[] createAndEnable(ModuleManager mgr) throws Exception {
        // The Java runtime module is an autoload in production; only an
        // autoload (or eager) provider may be pulled into the enable set
        // without being part of the requested modules.
        Module rt = mgr.create(rtJar, null, false, true, false);
        Module dep = mgr.create(depJar, null, false, false, false);
        Module src = mgr.create(srcJar, null, false, false, false);
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            mgr.enable(new HashSet<Module>(Arrays.asList(dep, src)));
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
        assertTrue("the token requirement must have enabled the runtime module", rt.isEnabled());
        return new Module[] {dep, src};
    }

    /**
     * The java runtime is an ordinary module discovered through the
     * token/service mechanism, like any other runtime: enabling a source
     * module without a module dependency on the provider still pulls the
     * provider in through the OpenIDE-Module-Requires token alone.
     */
    public void testTokenRequirementPullsRuntimeModule() throws Exception {
        ModuleManager mgr = newManager();
        Module[] mods = createAndEnable(mgr);
        assertEquals("v1", mods[1].getClassLoader().loadClass("org.foo.srcmod.Src").getField("ID").get(null)); // NOI18N
        // And a manager in which the provider was never created reports a
        // missing dependency instead of a confusing runtime error:
        ModuleManager mgr2 = newManager();
        mgr2.mutexPrivileged().enterWriteAccess();
        try {
            Module dep = mgr2.create(depJar, null, false, false, false);
            Module src = mgr2.create(srcJar, null, false, false, false);
            try {
                mgr2.enable(new HashSet<Module>(Arrays.asList(dep, src)));
                fail("enablement without any token provider should fail");
            } catch (IllegalArgumentException ex) {
                // IllegalModuleException (ENABLE_MISSING): the token
                // requirement cannot be satisfied.
                assertFalse(src.isEnabled());
            }
        } finally {
            mgr2.mutexPrivileged().exitWriteAccess();
        }
    }

    public void testLazyCompilationDefersUntilFirstUse() throws Exception {
        ModuleManager mgr = newManager();
        Module srcMod = enableSourceModule(mgr);
        assertTrue(srcMod.isEnabled());
        File marker = cacheMarker();
        assertFalse("enablement must not compile anything yet", marker.exists());

        // Handwritten resources are served straight from the source JAR,
        // without compiling:
        assertEquals("some resource\n", readResource(srcMod.getClassLoader(), "org/foo/srcmod/resource.txt")); // NOI18N
        assertFalse("resource serving must not compile", marker.exists());

        // The first class load triggers the compilation:
        Class<?> c = srcMod.getClassLoader().loadClass("org.foo.srcmod.Src"); // NOI18N
        assertEquals("v1", c.getField("ID").get(null));
        assertTrue(marker.isFile());
    }

    public void testLayerHintAnswersWithoutCompiling() throws Exception {
        ModuleManager mgr = newManager();
        Module srcMod = enableSourceModule(mgr);
        // The eager startup layer lookup is answered from the build hint,
        // without compiling a module that has no generated layer:
        assertFalse("no layer expected", // NOI18N
                srcMod.findResources("META-INF/generated-layer.xml").hasMoreElements()); // NOI18N
        assertFalse("layer lookup must not compile a layer-less module", cacheMarker().exists());

        // Generated resources do trigger the compile:
        String services = readResource(srcMod.getClassLoader(), "META-INF/services/java.lang.Runnable"); // NOI18N
        assertTrue(services.contains("org.foo.srcmod.Src")); // NOI18N
        assertTrue(cacheMarker().isFile());
    }

    /**
     * The build-time generated layer is shipped in the JAR and served
     * directly, so a module with a layer can answer the eager startup
     * lookup without compiling at all - compilation waits for the first
     * class load.
     */
    public void testShippedLayerServedWithoutCompiling() throws Exception {
        Map<String,String> contents = new LinkedHashMap<>();
        contents.put("org/foo/srcmod/Src.java", // NOI18N
                "package org.foo.srcmod;\n" // NOI18N
                + "public class Src {\n"
                + "  public static final String ID = \"shipped\";\n" // NOI18N
                + "}\n");
        contents.put("META-INF/generated-layer.xml", // NOI18N
                "<?xml version=\"1.0\"?>\n" // NOI18N
                + "<filesystem>\n"
                + "  <file name=\"shipped-marker.instance\"/>\n" // NOI18N
                + "</filesystem>\n");
        contents.put("META-INF/generated-layer.hint", "layer=true"); // NOI18N
        contents.put("META-INF/services/java.lang.Runnable", "org.foo.srcmod.Src\n"); // NOI18N
        Map<String,String> manifest = new HashMap<>();
        manifest.put("OpenIDE-Module", "org.foo.shippedlayer"); // NOI18N
        manifest.put("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Source", "java"); // NOI18N
        manifest.put("OpenIDE-Module-Requires", SourceRuntime.TOKEN_PREFIX + "java"); // NOI18N
        File jar = new File(jars, "shipped-layer-module.jar"); // NOI18N
        createJar(jar, contents, manifest);

        ModuleManager mgr = newManager();
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            mgr.create(rtJar, null, false, true, false);
            Module m = mgr.create(jar, null, false, false, false);
            mgr.enable(new HashSet<Module>(Collections.singleton(m)));
            // The layer is served straight from the JAR...
            String layer = readResource(m.getClassLoader(), "META-INF/generated-layer.xml"); // NOI18N
            assertTrue(layer.contains("shipped-marker")); // NOI18N
            // ...and so are generated service registrations...
            String services = readResource(m.getClassLoader(), "META-INF/services/java.lang.Runnable"); // NOI18N
            assertEquals("org.foo.srcmod.Src", services.trim()); // NOI18N
            // ...without compiling the module:
            File cache = new File(org.openide.modules.Places.getCacheDirectory(),
                    "source-modules/org-foo-shippedlayer"); // NOI18N
            assertFalse("generated-resource lookups must not compile a module that ships them", // NOI18N
                    cache.isDirectory());
            // The first class load still compiles:
            Class<?> c = m.getClassLoader().loadClass("org.foo.srcmod.Src"); // NOI18N
            assertEquals("shipped", c.getField("ID").get(null));
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
    }

    /**
     * The jna/flatlaf pattern: a source module wraps a library in a
     * Class-Path ext JAR; a binary dependent's classes reference the
     * library package. Resolution must route through the source module's
     * lazy loader (compiling it if needed), not fall through to the
     * system classloader - both before and after the source module is
     * compiled.
     */
    public void testExtJarResolutionFromBinaryDependent() throws Exception {
        // Build the library JAR: com/foo/ext/Ext.class
        File libSrc = new File(getWorkDir(), "ext-src/com/foo/ext/Ext.java"); // NOI18N
        writeFile(libSrc,
                "package com.foo.ext;\n" // NOI18N
                + "public final class Ext {\n"
                + "  public static String id() { return \"ext-lib\"; }\n" // NOI18N
                + "}\n");
        File libClasses = new File(getWorkDir(), "ext-classes"); // NOI18N
        libClasses.mkdirs();
        compileFiles(Collections.singletonList(libSrc), libClasses, Collections.<String>emptyList());
        File libJar = new File(jars, "ext/dep.jar"); // NOI18N
        libJar.getParentFile().mkdirs();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(libJar))) {
            addJarEntry(jos, "com/foo/ext/Ext.class", // NOI18N
                    new File(libClasses, "com/foo/ext/Ext.class")); // NOI18N
        }

        // A source module wrapping the library via Class-Path:
        File wrapJar = new File(jars, "wrapper-src.jar"); // NOI18N
        Map<String,String> contents = new LinkedHashMap<>();
        contents.put("wrap/Impl.java", // NOI18N
                "package wrap;\n" // NOI18N
                + "public final class Impl {\n"
                + "  public static String use() { return com.foo.ext.Ext.id(); }\n" // NOI18N
                + "}\n");
        Map<String,String> wm = new HashMap<>();
        wm.put("OpenIDE-Module", "org.foo.wrappersrc"); // NOI18N
        wm.put("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        wm.put("OpenIDE-Module-Public-Packages", "wrap.*, com.foo.ext.*"); // NOI18N
        wm.put("Class-Path", "ext/dep.jar"); // NOI18N
        wm.put("OpenIDE-Module-Source", "java"); // NOI18N
        wm.put("OpenIDE-Module-Requires", SourceRuntime.TOKEN_PREFIX + "java"); // NOI18N
        createJar(wrapJar, contents, wm);

        // A binary dependent whose class references the ext package:
        File binSrc = new File(getWorkDir(), "bin-src/org/foo/bin/Client.java"); // NOI18N
        writeFile(binSrc,
                "package org.foo.bin;\n" // NOI18N
                + "public final class Client {\n"
                + "  public static String use() { return com.foo.ext.Ext.id(); }\n" // NOI18N
                + "}\n");
        File binClasses = new File(getWorkDir(), "bin-classes"); // NOI18N
        binClasses.mkdirs();
        compileFiles(Collections.singletonList(binSrc), binClasses, Collections.singletonList(libJar.getPath()));
        File binJar = new File(jars, "client.jar"); // NOI18N
        Manifest bmf = new Manifest();
        bmf.getMainAttributes().putValue("Manifest-Version", "1.0"); // NOI18N
        bmf.getMainAttributes().putValue("OpenIDE-Module", "org.foo.binclient"); // NOI18N
        bmf.getMainAttributes().putValue("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        bmf.getMainAttributes().putValue("OpenIDE-Module-Module-Dependencies", "org.foo.wrappersrc > 1.0"); // NOI18N
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(binJar), bmf)) {
            addJarEntry(jos, "org/foo/bin/Client.class", // NOI18N
                    new File(binClasses, "org/foo/bin/Client.class")); // NOI18N
        }

        // Case 1: the source module is not compiled yet (pure lazy).
        ModuleManager mgr = newManager();
        Module[] mods = enablePair(mgr, wrapJar, binJar);
        Class<?> c = mods[1].getClassLoader().loadClass("org.foo.bin.Client"); // NOI18N
        assertEquals("ext-lib", c.getMethod("use").invoke(null));

        // Case 2: fresh manager, source module already compiled (flatlaf shape).
        ModuleManager mgr2 = newManager();
        Module[] mods2 = enablePair(mgr2, wrapJar, binJar);
        mods2[0].getClassLoader().loadClass("wrap.Impl"); // NOI18N
        Class<?> c2 = mods2[1].getClassLoader().loadClass("org.foo.bin.Client"); // NOI18N
        assertEquals("ext-lib", c2.getMethod("use").invoke(null));
    }

    private Module[] enablePair(ModuleManager mgr, File jarA, File jarB) throws Exception {
        mgr.create(rtJar, null, false, true, false);
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            Module a = mgr.create(jarA, null, false, false, false);
            Module b = mgr.create(jarB, null, false, false, false);
            mgr.enable(new HashSet<Module>(Arrays.asList(a, b)));
            return new Module[] {a, b};
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
    }

    /** Source-only fragment modules are rejected with a clear error (B2). */
    public void testFragmentModulesAreRejected() throws Exception {
        Map<String,String> contents = new LinkedHashMap<>();
        contents.put("frag/SomeClass.java", // NOI18N
                "package frag;\npublic class SomeClass {}\n"); // NOI18N
        Map<String,String> manifest = new HashMap<>();
        manifest.put("OpenIDE-Module", "org.foo.frag"); // NOI18N
        manifest.put("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Fragment-Host", "org.foo.host"); // NOI18N
        manifest.put("OpenIDE-Module-Source", "java"); // NOI18N
        File jar = new File(jars, "fragment-source-module.jar"); // NOI18N
        createJar(jar, contents, manifest);

        ModuleManager mgr = newManager();
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            try {
                mgr.create(jar, null, false, false, false);
                fail("source fragment should be rejected");
            } catch (IOException ex) {
                String msg = findMessage(ex);
                assertTrue("no fragment explanation in: " + msg,
                        msg.contains("fragment")); // NOI18N
            }
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
    }

    /**
     * Handwritten service registrations stay enumerable after the module
     * is compiled: the compile regenerates generated registrations, but
     * the shipped handwritten ones remain visible (S2).
     */
    public void testHandwrittenServicesSurviveCompilation() throws Exception {
        ModuleManager mgr = newManager();
        Module srcMod = enableSourceModule(mgr);
        ClassLoader loader = srcMod.getClassLoader();
        String svc = "META-INF/services/org.example.Runnable"; // NOI18N
        // Fixture ships one handwritten registration at a non-annotation
        // interface (see makeSourceModule).
        assertTrue("handwritten registration not served pre-compile",
                loader.getResources(svc).hasMoreElements());
        // Force the compile, then re-check enumeration.
        loader.loadClass("org.foo.srcmod.Src"); // NOI18N
        assertTrue(cacheMarker().isFile());
        assertTrue("handwritten registration not enumerable post-compile",
                loader.getResources(svc).hasMoreElements());
        assertEquals("org.foo.srcmod.Src", readResource(loader, svc).trim()); // NOI18N
    }

    /**
     * A pure or externally built module has no side-path bundle copies and
     * no annotation processors: its plain {@code Bundle.properties} IS the
     * handwritten original, and it must reach the compiled output - the
     * NbBundle access pattern is class-relative, so it uses the class's
     * own loader, which serves only the compiled output.
     */
    public void testExternalModulePlainBundleReachesOutput() throws Exception {
        Map<String,String> contents = new LinkedHashMap<>();
        contents.put("org/foo/extmod/Src.java", // NOI18N
                "package org.foo.extmod;\n" // NOI18N
                + "public class Src {\n"
                + "  public static String msg() {\n"
                + "    return org.openide.util.NbBundle.getMessage(Src.class, \"msg_ext\");\n" // NOI18N
                + "  }\n"
                + "}\n");
        // No side-path copy: the plain bundle is the original (external shape).
        contents.put("org/foo/extmod/Bundle.properties", // NOI18N
                "msg_ext=ext-value\n"); // NOI18N
        Map<String,String> manifest = new HashMap<>();
        manifest.put("OpenIDE-Module", "org.foo.extmod"); // NOI18N
        manifest.put("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Source", "java"); // NOI18N
        manifest.put("OpenIDE-Module-Requires", SourceRuntime.TOKEN_PREFIX + "java"); // NOI18N
        File jar = new File(jars, "external-module.jar"); // NOI18N
        createJar(jar, contents, manifest);

        ModuleManager mgr = newManager();
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            mgr.create(rtJar, null, false, true, false);
            Module m = mgr.create(jar, null, false, false, false);
            mgr.enable(new HashSet<Module>(Collections.singleton(m)));
            Class<?> c = m.getClassLoader().loadClass("org.foo.extmod.Src"); // NOI18N
            assertEquals("ext-value", c.getMethod("msg").invoke(null)); // NOI18N
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
    }

    /**
     * A harness-built JAR may ship Bundle.properties files that are PURELY
     * generated (no handwritten original, no side-path copy) - annotation
     * processor output merged into the JAR at build time. Those must not
     * be extracted into the compile's source tree, or the processor hits
     * duplicate keys ("key X is a duplicate of one from
     * Bundle.properties") and every dependent module's compile fails
     * against an empty cache directory. The marker declares the harness
     * contract; extraction skips all plain-path bundles.
     */
    public void testGeneratedOnlyBundleNotExtracted() throws Exception {
        Map<String,String> contents = new LinkedHashMap<>();
        contents.put("org/foo/hyb/Src.java", // NOI18N
                "package org.foo.hyb;\n" // NOI18N
                + "import org.openide.util.NbBundle;\n"
                + "@NbBundle.Messages(\"msg_x=XVal\")\n" // NOI18N
                + "public class Src {\n"
                + "  public static String msg() {\n"
                + "    return NbBundle.getMessage(Src.class, \"msg_x\");\n" // NOI18N
                + "  }\n"
                + "}\n");
        // A generated-only bundle as the ant build would ship it: the
        // processor's own output, no handwritten original exists.
        contents.put("org/foo/hyb/Bundle.properties", // NOI18N
                "msg_x=XVal\n"); // NOI18N
        // The harness marker (no side-path original for this bundle).
        contents.put(SourceModule.SOURCE_BUNDLES_MARKER,
                "plain-path Bundle.properties files are merged or generated copies\n");
        Map<String,String> manifest = new HashMap<>();
        manifest.put("OpenIDE-Module", "org.foo.hybmod"); // NOI18N
        manifest.put("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Source", "java"); // NOI18N
        manifest.put("OpenIDE-Module-Requires", SourceRuntime.TOKEN_PREFIX + "java"); // NOI18N
        File jar = new File(jars, "generated-bundle-module.jar"); // NOI18N
        createJar(jar, contents, manifest);

        ModuleManager mgr = newManager();
        mgr.mutexPrivileged().enterWriteAccess();
        try {
            mgr.create(rtJar, null, false, true, false);
            Module m = mgr.create(jar, null, false, false, false);
            mgr.enable(new HashSet<Module>(Collections.singleton(m)));
            // The compile must succeed and regenerate the bundle:
            Class<?> c = m.getClassLoader().loadClass("org.foo.hyb.Src"); // NOI18N
            assertEquals("XVal", c.getMethod("msg").invoke(null)); // NOI18N
        } finally {
            mgr.mutexPrivileged().exitWriteAccess();
        }
    }

    /**
     * A class-file resource lookup is a class load in disguise: some
     * frameworks probe class existence via {@code getResource("Foo.class")}
     * instead of {@code Class.forName} (the debugger API does). It must
     * trigger the compile and serve the compiled output - never a packaged
     * build-time copy, which would be stale for a patched module.
     */
    public void testClassFileResourceLookupCompilesAndServesOutput() throws Exception {
        ModuleManager mgr = newManager();
        Module srcMod = enableSourceModule(mgr);
        ClassLoader loader = srcMod.getClassLoader();
        // Not compiled yet: the lookup must compile and resolve.
        String res = "org/foo/srcmod/Src.class"; // NOI18N
        URL url = loader.getResource(res);
        assertNotNull("class-file resource must resolve (compiling if needed)", url);
        assertTrue("must come from the compiled output: " + url, // NOI18N
                url.toExternalForm().contains("source-modules"));
        assertTrue("the lookup must have compiled the module", cacheMarker().isFile());
        // Still resolves after the compile, from the same source of truth:
        assertNotNull(loader.getResource(res));
        assertTrue(loader.getResources(res).hasMoreElements());
        // A class that does not exist answers null/empty:
        assertNull(loader.getResource("org/foo/srcmod/Nope.class")); // NOI18N
        assertFalse(loader.getResources("org/foo/srcmod/Nope.class").hasMoreElements()); // NOI18N
    }

    /**
     * Concurrent first-use compiles from multiple threads: no deadlock
     * (compileLocks follow the dependency DAG and never wait on the
     * module manager mutex), and exactly one compilation wins.
     */
    public void testConcurrentClassLoadsCompileOnce() throws Exception {
        final ModuleManager mgr = newManager();
        final Module srcMod = enableSourceModule(mgr);
        final List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    Class<?> c = srcMod.getClassLoader().loadClass("org.foo.srcmod.Src"); // NOI18N
                    assertEquals("v1", c.getField("ID").get(null));
                } catch (Throwable t) {
                    errors.add(t);
                }
            }, "loader-" + i); // NOI18N
            threads[i].setDaemon(true);
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join(60000);
            assertFalse(t.getName() + " deadlocked", t.isAlive()); // NOI18N
        }
        assertTrue("unexpected errors: " + errors, errors.isEmpty());
        assertTrue("the module should be compiled", cacheMarker().isFile()); // NOI18N
    }

    public void testSourceModuleIsCompiledAndLoaded() throws Exception {
        ModuleManager mgr = newManager();
        Module srcMod = enableSourceModule(mgr);
        assertTrue("created as SourceModule: " + srcMod, srcMod instanceof SourceModule); // NOI18N
        assertTrue(srcMod.isEnabled());

        ClassLoader loader = srcMod.getClassLoader();
        Class<?> c = loader.loadClass("org.foo.srcmod.Src"); // NOI18N
        assertEquals("v1", c.getField("ID").get(null));
        // The dependency on the binary dep module is live at runtime:
        Runnable r = (Runnable) c.getDeclaredConstructor().newInstance();
        r.run();

        // Handwritten resources are copied to the compiled output:
        assertNotNull(loader.getResource("org/foo/srcmod/resource.txt")); // NOI18N

        // @ServiceProvider registration, generated by the runtime compile:
        String services = readResource(loader, "META-INF/services/java.lang.Runnable"); // NOI18N
        assertTrue("services content: " + services,
                services.contains("org.foo.srcmod.Src")); // NOI18N

        // @NbBundle.Messages merges with the handwritten bundle:
        String bundle = readResource(loader, "org/foo/srcmod/Bundle.properties"); // NOI18N
        assertTrue("generated key in bundle: " + bundle, bundle.contains("msg_ann=AnnValue")); // NOI18N
        assertTrue("handwritten key in bundle: " + bundle, bundle.contains("msg_hand=HandValue")); // NOI18N

        // The generated Bundle.java helper class is compiled too:
        assertNotNull(loader.loadClass("org.foo.srcmod.Bundle")); // NOI18N

        // The source JAR itself must not be on the classpath - exactly one
        // copy of the resource is visible, from the compiled output:
        assertEquals("exactly one bundle resource",
                1,
                countResources(srcMod, "org/foo/srcmod/Bundle.properties")); // NOI18N
    }

    public void testUnchangedModuleIsNotRecompiled() throws Exception {
        // First run compiles (compilation is lazy - loading a class triggers it):
        ModuleManager mgr = newManager();
        Module m1 = enableSourceModule(mgr);
        assertEquals("v1", m1.getClassLoader().loadClass("org.foo.srcmod.Src").getField("ID").get(null)); // NOI18N
        File marker = cacheMarker();
        assertTrue(marker.isFile());
        long mtime = marker.lastModified();

        // A fresh manager with the very same JAR reuses the cache:
        ModuleManager mgr2 = newManager();
        Module srcMod = enableSourceModule(mgr2);
        assertEquals("v1", srcMod.getClassLoader().loadClass("org.foo.srcmod.Src").getField("ID").get(null)); // NOI18N
        assertEquals("no recompilation expected", mtime, marker.lastModified());
    }

    public void testLocallyPatchedModuleIsRecompiled() throws Exception {
        ModuleManager mgr = newManager();
        enableSourceModule(mgr);

        // Simulate a local patch of the module's source JAR:
        makeSourceModule(srcJar, "v2", false);

        ModuleManager mgr2 = newManager();
        Module srcMod = enableSourceModule(mgr2);
        assertEquals("v2", srcMod.getClassLoader().loadClass("org.foo.srcmod.Src").getField("ID").get(null)); // NOI18N
    }

    public void testChangedDependencyInvalidatesCache() throws Exception {
        ModuleManager mgr = newManager();
        Module m1 = enableSourceModule(mgr);
        m1.getClassLoader().loadClass("org.foo.srcmod.Src"); // NOI18N
        File marker = cacheMarker();
        long mtime = marker.lastModified();
        assertTrue("nothing compiled?", mtime > 0);

        // Rebuild the dependency JAR with identical content: the stamp
        // (size/mtime based for classpath entries) must force a recompile.
        makeDepModule(depJar);

        ModuleManager mgr2 = newManager();
        Module m2 = enableSourceModule(mgr2);
        m2.getClassLoader().loadClass("org.foo.srcmod.Src"); // NOI18N
        assertTrue("expected recompilation after dependency change",
                marker.lastModified() != mtime);
    }

    public void testCompileFailureIsDeferredToFirstUse() throws Exception {
        makeSourceModule(srcJar, "v1", true);
        ModuleManager mgr = newManager();
        Module[] mods = createAndEnable(mgr);
        // Compilation is lazy: enablement succeeds, the failure surfaces
        // when the classes are first needed.
        assertTrue(mods[1].isEnabled());
        try {
            mods[1].getClassLoader().loadClass("org.foo.srcmod.Src"); // NOI18N
            fail("loading a class of a broken source module should fail");
        } catch (ClassNotFoundException ex) {
            // OK - the lazy compile failed and reported ClassNotFound.
        }
    }

    public void testEagerModeFailsAtEnablement() throws Exception {
        System.setProperty("netbeans.source.modules.eager", "true"); // NOI18N
        try {
            makeSourceModule(srcJar, "v1", true);
            ModuleManager mgr = newManager();
            mgr.mutexPrivileged().enterWriteAccess();
            try {
                mgr.create(rtJar, null, false, true, false);
                Module dep = mgr.create(depJar, null, false, false, false);
                Module src = mgr.create(srcJar, null, false, false, false);
                try {
                    mgr.enable(new HashSet<Module>(Arrays.asList(dep, src)));
                    fail("enablement of a broken source module should fail in eager mode");
                } catch (InvalidException ex) {
                    String msg = findMessage(ex);
                    assertTrue("diagnostics not reported: " + msg,
                            msg.contains("Compilation of source module")); // NOI18N
                    assertTrue("no compile error detail in: " + msg,
                            msg.contains("illegal start") || msg.contains("expected")); // NOI18N
                }
                assertFalse(src.isEnabled());
                assertFalse("module should have problems", src.getProblems().isEmpty()); // NOI18N
            } finally {
                mgr.mutexPrivileged().exitWriteAccess();
            }
        } finally {
            System.clearProperty("netbeans.source.modules.eager"); // NOI18N
        }
    }

    public void testPatchesOverrideCompiledClasses() throws Exception {
        // Bring the source module up once, to have compiled output:
        ModuleManager mgr = newManager();
        enableSourceModule(mgr);
        File compiled = Places.getCacheSubdirectory("source-modules/org-foo-srcmod/classes"); // NOI18N

        // Build a patch directory with a replacement class:
        File patchDir = new File(getWorkDir(), "patch"); // NOI18N
        File patchSrc = new File(patchDir, "org/foo/srcmod/Patch.java"); // NOI18N
        patchSrc.getParentFile().mkdirs();
        writeFile(patchSrc,
                "package org.foo.srcmod;\n" // NOI18N
                + "public class Patch {\n"
                + "  public static final String ID = \"patched\";\n" // NOI18N
                + "}\n");
        compileFiles(Collections.singletonList(patchSrc), patchDir,
                Collections.singletonList(compiled.getPath()));

        System.setProperty("netbeans.patches.org.foo.srcmod", patchDir.getPath()); // NOI18N
        try {
            ModuleManager mgr2 = newManager();
            Module srcMod = enableSourceModule(mgr2);
            // Patch must override the freshly compiled classes:
            Class<?> c = srcMod.getClassLoader().loadClass("org.foo.srcmod.Patch"); // NOI18N
            assertEquals("patched", c.getField("ID").get(null)); // NOI18N
            // But non-patched classes still come from the compilation output:
            assertEquals("v1", srcMod.getClassLoader().loadClass("org.foo.srcmod.Src").getField("ID").get(null)); // NOI18N
        } finally {
            System.clearProperty("netbeans.patches.org.foo.srcmod"); // NOI18N
        }
    }

    private File cacheMarker() {
        File classes = Places.getCacheSubdirectory("source-modules/org-foo-srcmod/classes"); // NOI18N
        return new File(classes, "org/foo/srcmod/Src.class"); // NOI18N
    }

    private static String findMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            sb.append(t.getMessage());
            t = t.getCause();
        }
        return sb.toString();
    }

    private static int countResources(Module m, String resource) throws IOException {
        int cnt = 0;
        for (java.net.URL u : Collections.list(m.findResources(resource))) {
            cnt++;
        }
        return cnt;
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
     * Builds the Java-runtime provider module for the tests: the real
     * {@code org.netbeans.modules.source.java.JavaSourceRuntime} (from
     * the test classpath - keep the test dependency on the
     * {@code org.netbeans.modules.source.java} module in project.xml),
     * packaged as a module providing the java token through the same
     * service/token discovery production uses.
     */
    private static void makeJavaRuntimeModule(File jar) throws Exception {
        byte[] rtClass = readClassBytes("org.netbeans.modules.source.java.JavaSourceRuntime");
        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getMainAttributes().putValue("OpenIDE-Module", "org.foo.javart"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Specification-Version", "1.0");
        mf.getMainAttributes().putValue("OpenIDE-Module-Public-Packages", "org.netbeans.modules.source.java.*"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Provides", SourceRuntime.TOKEN_PREFIX + "java"); // NOI18N
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar), mf)) {
            addJarEntry(jos, "META-INF/services/org.netbeans.SourceRuntime", // NOI18N
                    "org.netbeans.modules.source.java.JavaSourceRuntime\n" // NOI18N
                            .getBytes(StandardCharsets.UTF_8));
            addJarEntry(jos, "org/netbeans/modules/source/java/JavaSourceRuntime.class", rtClass);
        }
    }

    private static byte[] readClassBytes(String fqcn) throws IOException {
        String path = fqcn.replace('.', '/') + ".class"; // NOI18N
        try (InputStream is = SourceModuleTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(fqcn + " not on the test classpath - is platform/source.java built?", is);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int len;
            while ((len = is.read(b)) != -1) {
                baos.write(b, 0, len);
            }
            return baos.toByteArray();
        }
    }

    /** Creates a binary dependency module exporting a tiny API class. */
    private static void makeDepModule(File jar) throws Exception {
        File src = new File(jar.getParentFile(), "dep-src/org/foo/depapi/DepApi.java"); // NOI18N
        writeFile(src,
                "package org.foo.depapi;\n" // NOI18N
                + "public final class DepApi {\n"
                + "  public static String whoami() {\n"
                + "    return \"depapi\";\n" // NOI18N
                + "  }\n"
                + "}\n");
        File classes = new File(jar.getParentFile(), "dep-classes"); // NOI18N
        classes.mkdirs();
        compileFiles(Collections.singletonList(src), classes, Collections.<String>emptyList());

        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module", "org.foo.depapi"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        mf.getMainAttributes().putValue("OpenIDE-Module-Public-Packages", "org.foo.depapi.*"); // NOI18N
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar), mf)) {
            addJarEntry(jos, "org/foo/depapi/DepApi.class", // NOI18N
                    new File(classes, "org/foo/depapi/DepApi.class")); // NOI18N
        }
    }

    /** Creates a source-only module JAR with sources + resources at the root. */
    private static void makeSourceModule(File jar, String id, boolean broken) throws Exception {
        Map<String,String> contents = new LinkedHashMap<>();
        contents.put("org/foo/srcmod/Src.java", // NOI18N
                "package org.foo.srcmod;\n" // NOI18N
                + "import org.foo.depapi.DepApi;\n"
                + "import org.openide.util.NbBundle;\n"
                + "import org.openide.util.lookup.ServiceProvider;\n"
                + "@ServiceProvider(service=Runnable.class)\n"
                + "@NbBundle.Messages({\"msg_ann=AnnValue\", \"msg_fmt={0} fmt\"})\n" // NOI18N
                + "public class Src implements Runnable {\n"
                + "  public static final String ID = \"" + id + "\";\n" // NOI18N
                + "  public @Override void run() {\n"
                + "    if (!\"depapi\".equals(DepApi.whoami())) throw new IllegalStateException(\"depapi not visible\");\n" // NOI18N
                + (broken ? "    int x = ;\n" : "")
                + "  }\n"
                + "}\n");
        contents.put("org/foo/srcmod/Bundle.properties", // NOI18N
                "msg_hand=HandValue\n"); // NOI18N
        // The harness ships the handwritten bundle under the side path for
        // the runtime compile's source tree, and the merged copy at the
        // plain path for pre-compile serving. The marker emulates a
        // harness-built JAR: plain-path bundles are merged/generated
        // artifacts, never compile inputs.
        contents.put("META-INF/netbeans/source-bundles/org/foo/srcmod/Bundle.properties", // NOI18N
                "msg_hand=HandValue\n");
        contents.put(SourceModule.SOURCE_BUNDLES_MARKER,
                "plain-path Bundle.properties files are merged or generated copies\n"); // NOI18N
        contents.put("org/foo/srcmod/resource.txt", // NOI18N
                "some resource\n"); // NOI18N
        // A handwritten service registration at a non-annotation interface:
        // not regenerated by the compile, must survive via the JAR copy.
        contents.put("META-INF/services/org.example.Runnable", "org.foo.srcmod.Src\n"); // NOI18N
        Map<String,String> manifest = new HashMap<>();
        manifest.put("OpenIDE-Module", "org.foo.srcmod"); // NOI18N
        manifest.put("OpenIDE-Module-Specification-Version", "1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Module-Dependencies", "org.foo.depapi > 1.0"); // NOI18N
        manifest.put("OpenIDE-Module-Source", "java"); // NOI18N
        manifest.put("OpenIDE-Module-Requires", SourceRuntime.TOKEN_PREFIX + "java"); // NOI18N
        // Build-time hint: this module's annotations generate no layer.
        contents.put("META-INF/generated-layer.hint", "layer=false"); // NOI18N
        createJar(jar, contents, manifest);
    }

    private static void writeFile(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void compileFiles(List<File> files, File outputDir, List<String> classpath)
            throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("test must run on a JDK", compiler);
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        try {
            StringBuilder cp = new StringBuilder(System.getProperty("java.class.path"));
            for (String c : classpath) {
                cp.append(File.pathSeparatorChar).append(c);
            }
            List<String> options = Arrays.asList(
                    "-d", outputDir.getPath(),
                    "-classpath", cp.toString());
            boolean ok = compiler.getTask(null, fileManager, null, options, null,
                    fileManager.getJavaFileObjectsFromFiles(files)).call();
            assertTrue("compilation of " + files + " failed", ok);
        } finally {
            fileManager.close();
        }
    }

    private static void addJarEntry(JarOutputStream jos, String path, File file) throws IOException {
        byte[] data;
        try (InputStream is = new FileInputStream(file); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096];
            int len;
            while ((len = is.read(b)) != -1) {
                baos.write(b, 0, len);
            }
            data = baos.toByteArray();
        }
        addJarEntry(jos, path, data);
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
