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
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.logging.Level;
import org.openide.modules.Dependency;

    /**
     * A module shipped as source code only. The JAR layout is that of a regular
     * module (complete manifest, resources at the root), except that it holds
     * source files where a binary module holds compiled classes; the
     * manifest must carry {@code OpenIDE-Module-Source: <runtime id>}
     * ({@code true} meaning the built-in Java runtime).
     * <p>
     * When the module is enabled, a {@link SourceRuntime} compiles/materializes
     * its sources on demand (see {@link SourceModuleCompiler}) into the user
     * directory cache and the module classloader is built over the compiled
     * output directory - which {@link JarClassLoader} supports natively, like
     * for patched modules. The source JAR itself is deliberately <em>not</em>
     * part of the classpath, so resources are never seen twice.
     * <p>
     * If the module is patched locally (or any of its dependencies changes), the
     * cached compilation is detected as stale and the module is recompiled at the
     * next enablement.
     *
     * @see SourceRuntime
     * @see SourceModuleCompiler
     * @since org.netbeans.base/1
     */
class SourceModule extends StandardModule {

    /** Manifest header marking a module JAR as source-only; value: the runtime id. */
    public static final String SOURCE_HEADER = "OpenIDE-Module-Source"; // NOI18N

    /**
     * Directory with the compiled output of this module.
     * Assigned in {@link #classLoaderUp} before the loader is created;
     * also read by dependent source modules when they assemble their
     * own compilation classpath.
     */
    private File compiledClasses;

    /** The source runtime responsible for this module, resolved at enablement. */
    private SourceRuntime runtime;

    /** Use ModuleManager.create as a factory. */
    public SourceModule(ModuleManager mgr, Events ev, File jar, Object history, boolean reloadable, boolean autoload, boolean eager) throws IOException {
        super(mgr, ev, jar, history, reloadable, autoload, eager);
    }

    /**
     * The runtime id requested by the given manifest, or null for a plain
     * binary module. {@code true} is normalized to the Java runtime.
     */
    static String runtimeId(Manifest mf) {
        if (mf == null) {
            return null;
        }
        String value = mf.getMainAttributes().getValue(SOURCE_HEADER);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return "true".equalsIgnoreCase(value) ? "java" : value; // NOI18N
    }

    /** Directory this module was compiled into, or null if not yet compiled. */
    File getCompiledClasses() {
        return compiledClasses;
    }

    /**
     * Compile the module if needed, then bring up a classloader over the
     * compiled output - the same construction as
     * {@link StandardModule#classLoaderUp}, except that the compiled classes
     * directory replaces the (source) module JAR. Interpreter-mode runtimes
     * instead supply a loader serving the module directly from source form
     * (see {@link SourceRuntime#createSourceLoader}).
     */
    @Override
    protected void classLoaderUp(Set<Module> parents) throws IOException {
        if (Util.err.isLoggable(Level.FINE)) {
            Util.err.log(Level.FINE, "classLoaderUp on source module {0} with parents {1}", new Object[] {this, parents});
        }
        runtime = resolveRuntime();

        // Find classloaders for dependent modules and parent to them.
        List<ClassLoader> loaders = new ArrayList<>(parents.size() + 1);
        // This should really be the base loader created by org.nb.Main for loading openide etc.:
        loaders.add(Module.class.getClassLoader());
        for (Module parent: parents) {
            PackageExport[] exports = parent.getPublicPackages();
            if (exports != null && exports.length == 0) {
                // Check if there is an impl dep here.
                boolean implDep = false;
                for (Dependency dep : getDependenciesArray()) {
                    if (dep.getType() == Dependency.TYPE_MODULE &&
                            dep.getComparison() == Dependency.COMPARE_IMPL &&
                            dep.getName().equals(parent.getCodeName())) {
                        implDep = true;
                        break;
                    }
                }
                if (!implDep) {
                    // Nothing exported from here at all, no sense even adding it.
                    // Shortcut; would not harm anything to add it now, but we would
                    // never use it anyway.
                    // Cf. #27853.
                    continue;
                }
            }
            ClassLoader l = getParentLoader(parent);
            if (parent.isFixed() && loaders.contains(l)) {
                Util.err.log(Level.FINE, "#24996: skipping duplicate classloader from {0}", parent);
                continue;
            }
            loaders.add(l);
        }

        // Interpreter mode: the runtime serves classes and resources from
        // source form; nothing is materialized or cached.
        ClassLoader sourceLoader = runtime.createSourceLoader(
                this, computeClasspath(), loaders.toArray(new ClassLoader[0]));
        if (sourceLoader != null) {
            Util.err.log(Level.FINE, "source module {0} interpreted by {1}", new Object[] {this, runtime.getId()});
            classloader = new RuntimeModuleClassLoader(sourceLoader, loaders.toArray(new ClassLoader[0]));
            return;
        }

        // Bytecode mode: make sure we are compiled and up to date before
        // anything looks for classes in this module.
        compiledClasses = SourceModuleCompiler.compileIfNeeded(this, runtime, computeClasspath());

        // Patches first, then the freshly compiled classes, then any
        // Class-Path extension JARs (resolved relative to the source JAR,
        // which sits in the cluster's modules/ dir next to modules/ext/).
        List<File> classp = new ArrayList<>(3);
        Set<File> ptchs = findPatches();
        if (ptchs != null) classp.addAll(ptchs);
        classp.add(compiledClasses);

        ((StandardModuleData)data()).addCp(classp);

        // possibly inject some patches
        getManager().refineModulePath(this, classp);

        // #27853
        ClassLoader cld = getManager().refineClassLoader(this, loaders);
        // the classloader may be shared, if this module is a fragment
        if (cld != null) {
            classloader = cld;
        } else {
            try {
                classloader = createNewClassLoader(classp, loaders);
            } catch (IllegalArgumentException iae) {
                // Should not happen, but just in case.
                throw new IOException(iae);
            }
        }
    }

    /**
     * Resolves the source runtime requested by this module's manifest.
     * Non-Java runtimes must be backed by an enabled module, so the
     * manifest must require the matching token - otherwise the module
     * system has no reason to enable the runtime first.
     */
    private SourceRuntime resolveRuntime() throws IOException {
        String id = runtimeId(getManifest());
        String token = SourceRuntime.TOKEN_PREFIX + id;
        boolean javaRuntime = "java".equals(id); // NOI18N
        if (!javaRuntime) {
            String requires = getManifest().getMainAttributes().getValue("OpenIDE-Module-Requires"); // NOI18N
            if (requires == null || !requires.contains(token)) {
                throw new IOException(
                    "Source module " + getCodeNameBase() + " uses runtime '" + id + "' " // NOI18N
                    + "but does not require the token " + token + ". " // NOI18N
                    + "Declare OpenIDE-Module-Requires: " + token // NOI18N
                    + " and have a module providing the token (OpenIDE-Module-Provides: " + token + ")."); // NOI18N
            }
        }
        return SourceRuntime.find(getManager(), id);
    }

    /**
     * Computes the compilation classpath for this module: the JARs (or
     * compiled outputs) of all declared module dependencies, transitively,
     * including their patches and {@code Class-Path} extensions, plus this
     * module's own extensions; and the JARs of the fixed (boot) modules.
     * The latter have no JAR list of their own - they are loaded from the
     * startup classpath - so we enumerate the manifests visible to their
     * classloaders; in a regular startup this is MainImpl's BootClassLoader
     * covering both platform lib/ and core/.
     */
    private List<File> computeClasspath() throws IOException {
        LinkedHashSet<File> cp = new LinkedHashSet<>();
        // Own patches and Class-Path extensions (but not the source JAR itself).
        for (File f : getAllJars()) {
            if (!f.equals(getJarFile())) {
                cp.add(f);
            }
        }
        collectDependencyJars(this, cp, new HashSet<String>());
        Set<ClassLoader> bootLoaders = new HashSet<>();
        bootLoaders.add(Module.class.getClassLoader());
        for (Module m : getManager().getModules()) {
            if (m.isFixed()) {
                try {
                    bootLoaders.add(m.getClassLoader());
                } catch (IllegalArgumentException ex) {
                    // Not enabled yet - e.g. an eager fixed module; skip.
                }
            }
        }
        for (ClassLoader boot : bootLoaders) {
            Enumeration<URL> manifests = boot.getResources("META-INF/MANIFEST.MF"); // NOI18N
            while (manifests.hasMoreElements()) {
                File jar = SourceModuleCompiler.jarUrlToFile(manifests.nextElement());
                if (jar != null && jar.isFile()) {
                    cp.add(jar);
                }
            }
        }
        return new ArrayList<>(cp);
    }

    /** Adds JARs (or compiled dirs) of all transitive module dependencies. */
    private void collectDependencyJars(Module m, Set<File> cp, Set<String> visited) {
        for (Dependency dep : m.getDependenciesArray()) {
            if (dep.getType() != Dependency.TYPE_MODULE) {
                // Token provides do not contribute classes.
                continue;
            }
            String name = (String) Util.parseCodeName(dep.getName())[0];
            if (!visited.add(name)) {
                continue;
            }
            Module parent = getManager().get(name);
            if (parent == null) {
                // Unsatisfied dependency - reported elsewhere with a proper error.
                continue;
            }
            if (parent instanceof SourceModule) {
                // A source module: compile against its compiled output
                // (already brought up, since dependencies are enabled first),
                // plus its patches and Class-Path extensions.
                File classes = ((SourceModule) parent).getCompiledClasses();
                if (classes != null) {
                    cp.add(classes);
                }
                for (File f : parent.getAllJars()) {
                    if (!f.equals(parent.getJarFile())) {
                        cp.add(f);
                    }
                }
            } else {
                cp.addAll(parent.getAllJars());
            }
            collectDependencyJars(parent, cp, visited);
        }
    }

    @Override
    public void reload() throws IOException {
        // The recompile-on-enable machinery will pick up any changes:
        // the stamp check in SourceModuleCompiler compares against the
        // source JAR contents afresh on every classLoaderUp.
        super.reload();
    }

    public @Override String toString() {
        String s = "SourceModule:" + getCodeNameBase() + " jarFile: " + getJarFile(); // NOI18N
        if (!isValid()) s += "[invalid]"; // NOI18N
        return s;
    }

    /**
     * Class loader for interpreter-mode source modules: a standard
     * multi-parented module classloader (package coverage, resource
     * delegation policy, permissions, module identity) whose "own"
     * classes and resources come from the runtime's loader, which serves
     * them directly from source form.
     */
    private final class RuntimeModuleClassLoader extends ProxyClassLoader implements Util.ModuleProvider {

        private final ClassLoader delegate;

        RuntimeModuleClassLoader(ClassLoader delegate, ClassLoader[] parents) {
            super(parents, false);
            this.delegate = delegate;
            addCoveredPackages(packagesOfSourceJar());
        }

        public @Override Module getModule() {
            return SourceModule.this;
        }

        /** Serves classes from the source form via the runtime. */
        protected @Override Class<?> doLoadClass(String pkg, String name) {
            try {
                return delegate.loadClass(name);
            } catch (ClassNotFoundException ex) {
                return null;
            }
        }

        public @Override java.net.URL findResource(String name) {
            return delegate.getResource(name);
        }

        public @Override java.util.Enumeration<java.net.URL> findResources(String name) throws IOException {
            return delegate.getResources(name);
        }

        protected @Override boolean shouldDelegateResource(String pkg, ClassLoader parent) {
            if (!super.shouldDelegateResource(pkg, parent)) {
                return false;
            }
            Module other;
            if (parent instanceof Util.ModuleProvider mp) {
                other = mp.getModule();
            } else {
                other = null;
            }
            return getManager().shouldDelegateResource(SourceModule.this, other, pkg, parent);
        }

        public @Override String toString() {
            return "RuntimeModuleCL@" + Integer.toHexString(System.identityHashCode(this)) // NOI18N
                    + "[" + getCodeNameBase() + "/" + runtime.getId() + "]"; // NOI18N
        }
    }

    /** Packages present in the module's source JAR, for classloader coverage. */
    private Set<String> packagesOfSourceJar() {
        Set<String> pkgs = new HashSet<>();
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(getJarFile())) {
            java.util.Enumeration<java.util.jar.JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.endsWith("/") || n.equals("META-INF/MANIFEST.MF")) { // NOI18N
                    continue;
                }
                int slash = n.lastIndexOf('/');
                if (slash > 0) {
                    // e.g. lang/Hello.lang -> lang,
                    //      META-INF/services/java.lang.Runnable -> META-INF.services
                    pkgs.add(n.substring(0, slash).replace('/', '.'));
                }
            }
        } catch (IOException ex) {
            Util.err.log(java.util.logging.Level.WARNING, "Cannot enumerate packages of " + getJarFile(), ex);
        }
        return pkgs;
    }

}
