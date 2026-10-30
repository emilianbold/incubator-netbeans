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
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import org.openide.modules.Dependency;
import org.openide.modules.Places;

/**
 * A module shipped as source code only. The JAR layout is that of a regular
 * module (complete manifest, resources at the root), except that it holds
 * source files where a binary module holds compiled classes; the
 * manifest must carry {@code OpenIDE-Module-Source: <runtime id>}
 * ({@code true} is accepted as a legacy alias for the Java runtime)
 * plus the matching token requirement, so that the module system
 * enables the runtime provider module before this module.
 * <p>
 * When the module is enabled, a {@link SourceRuntime} compiles/materializes
 * its sources on demand (see {@link SourceModuleCompiler}) into the user
 * directory cache and the module classloader is built over the compiled
 * output directory - which {@link JarClassLoader} supports natively, like
 * for patched modules. The source JAR itself is deliberately <em>not</em>
 * part of the classpath, so resources are never seen twice.
 * <p>
 * Compilation is <em>lazy</em>: module enablement only creates the loader;
 * the actual build happens on first use - the first class load, or the
 * first resource lookup that needs generated output. Resources present in
 * the source JAR (handwritten layers, icons, bundles in their plain form)
 * are served straight from the JAR without compiling, since the compile
 * would copy them verbatim anyway; the harness ships a
 * {@code META-INF/generated-layer.hint} so that modules without generated
 * layers can answer the eager startup layer lookup without compiling at
 * all. Set {@code -Dnetbeans.source.modules.eager=true} to compile at
 * enablement time instead (fail-fast behaviour).
 * <p>
 * If the module is patched locally (or any of its dependencies changes), the
 * cached compilation is detected as stale and the module is recompiled at
 * the next use.
 *
 * @see SourceRuntime
 * @see SourceModuleCompiler
 * @since 2.115
 */
class SourceModule extends StandardModule {

    /** Manifest header marking a module JAR as source-only; value: the runtime id. */
    public static final String SOURCE_HEADER = "OpenIDE-Module-Source"; // NOI18N

    /**
     * Build-time hint entry telling whether the annotation processors
     * generate a layer for this module ("layer=true"/"layer=false"), so
     * that modules without generated layers can answer the eager startup
     * lookup of {@code META-INF/generated-layer.xml} without compiling.
     */
    static final String LAYER_HINT_ENTRY = "META-INF/generated-layer.hint"; // NOI18N

    /** The generated layer resource, looked up eagerly for every enabled module. */
    static final String GENERATED_LAYER_ENTRY = "META-INF/generated-layer.xml"; // NOI18N

    /** Side path in hybrid JARs carrying the original handwritten bundles. */
    static final String SOURCE_BUNDLES_PREFIX = "META-INF/netbeans/source-bundles/"; // NOI18N

    /**
     * Marker a harness-built source JAR carries in the side path: every
     * {@code Bundle.properties} at a plain path in that JAR is a merged or
     * purely generated build artifact, never a compile input - the compile
     * inputs are the side-path originals. Externally built source JARs
     * carry no marker: their plain bundles are their originals.
     */
    static final String SOURCE_BUNDLES_MARKER = SOURCE_BUNDLES_PREFIX + ".merged-plain-bundles"; // NOI18N

    /** Set to compile at enablement instead of first use. */
    private static final String EAGER_PROP = "netbeans.source.modules.eager"; // NOI18N

    /**
     * Dedicated lock for the compile critical section. Deliberately NOT the
     * module monitor and NOT held while acquiring the module manager's
     * mutex (or vice versa): ensureCompiled is invoked from classloading
     * threads (holding the loader monitor) and from enablement threads
     * (holding the manager's write mutex), and any lock that is held
     * across a wait on the manager mutex can deadlock against writers that
     * touch the same module (Class.forName of an installer class, layer
     * lookups). Holding compileLock only ever waits for a dependency's
     * compileLock (an acyclic graph), never for the manager.
     */
    private final Object compileLock = new Object();

    /**
     * Directory with the compiled output of this module.
     * Volatile: published by ensureCompiled, read lock-free.
     */
    private volatile File compiledClasses;

    /** The source runtime for this module, resolved at enablement
     *  (write mutex held there - SourceRuntime.find inspects the manager). */
    private volatile SourceRuntime runtime;

    /**
     * The javac classpath snapshot computed at enablement: stable file
     * entries (own patches/extensions, binary dependency JARs, fragment
     * hosts/fragments, boot JARs) with source-module dependencies
     * represented by their fixed cache directory (created on demand), plus
     * the source dependencies to force-compile at use time.
     */
    private volatile List<File> snapshotClasspath;
    private volatile List<SourceModule> snapshotSourceDeps;
    /** Own patches + Class-Path extensions + fragment injections, to build
     *  the real loader's file list around the compiled output. */
    private volatile List<File> snapshotLoaderPrefix;
    private volatile List<File> snapshotLoaderSuffix;

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

    /** The loader over the compiled output, once {@link #ensureCompiled} ran. */
    private volatile ClassLoader realLoader;

    /** Parent loaders of the module, for the lazily created real loader. */
    private volatile List<ClassLoader> moduleParents;

    /** Cached compile failure, so failed compiles don't retry on every class load. */
    private volatile String compileFailure;

    /**
     * Compiles the module if needed and returns the output directory.
     * Safe to call from any context - including while holding the module
     * manager's mutex, a ProxyClassLoader monitor, or the module monitor -
     * because it never touches the manager: all manager-dependent inputs
     * (dependency resolution, patch/extension lists, runtime resolution)
     * are snapshotted at enablement (classLoaderUp, under the write mutex),
     * and source dependencies are compiled recursively holding only their
     * compileLocks (the dependency graph is acyclic, so this cannot
     * deadlock).
     */
    File ensureCompiled() throws IOException {
        File out = compiledClasses;
        if (out != null) {
            return out;
        }
        String failure = compileFailure;
        if (failure != null) {
            throw new IOException(failure);
        }
        SourceRuntime rt = runtime;
        List<File> cp = snapshotClasspath;
        List<SourceModule> deps = snapshotSourceDeps;
        if (rt == null || cp == null) {
            throw new IOException("Source module " + getCodeNameBase() + " is not enabled"); // NOI18N
        }
        synchronized (compileLock) {
            out = compiledClasses;
            if (out != null) {
                return out;
            }
            failure = compileFailure;
            if (failure != null) {
                throw new IOException(failure);
            }
            try {
                // Laziness propagates: dependencies compile on demand, here,
                // holding only their compileLocks (never the manager mutex).
                if (deps != null) {
                    for (SourceModule dep : deps) {
                        try {
                            dep.ensureCompiled();
                        } catch (IOException ex) {
                            Util.err.log(Level.WARNING, "Cannot compile dependency {0} of {1}: {2}", // NOI18N
                                    new Object[] {dep, this, ex});
                        }
                    }
                }
                File output = SourceModuleCompiler.compileIfNeeded(this, rt, cp);
                // Real loader file list: patches first, then the freshly
                // compiled classes, then extensions and fragment injections
                // (snapshotted at enablement).
                List<File> classp = new ArrayList<>(snapshotLoaderPrefix.size() + 1 + snapshotLoaderSuffix.size());
                classp.addAll(snapshotLoaderPrefix);
                classp.add(output);
                classp.addAll(snapshotLoaderSuffix);
                try {
                    realLoader = createNewClassLoader(classp, moduleParents);
                } catch (IllegalArgumentException iae) {
                    throw new IOException(iae);
                }
                // Make resources and ext-jar packages that only exist in the
                // compiled output or the Class-Path extensions reachable
                // through the lazy loader as well.
                if (classloader instanceof LazySourceModuleClassLoader lazy) {
                    lazy.coverClasspath(classp);
                }
                compiledClasses = output;
                compileFailure = null;
            } catch (IOException ex) {
                if (compileFailure == null) {
                    Util.err.log(Level.WARNING, "Compilation of source module {0} failed (will retry after reload): {1}", // NOI18N
                            new Object[] {this, ex.getMessage()});
                }
                compileFailure = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                throw ex;
            }
        }
        return compiledClasses;
    }

    /**
     * Bring up the module loader. Interpreter-mode runtimes supply a loader
     * serving the module directly from source form; bytecode mode creates a
     * lazy loader that compiles on first use.
     */
    @Override
    protected void classLoaderUp(Set<Module> parents) throws IOException {
        if (Util.err.isLoggable(Level.FINE)) {
            Util.err.log(Level.FINE, "classLoaderUp on source module {0} with parents {1}", new Object[] {this, parents});
        }

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

        // Resolve the runtime eagerly: SourceRuntime.find inspects the
        // manager, so it must happen here, under the write mutex.
        // ensureCompiled (called from arbitrary lock contexts) only
        // reads the result. This also enforces the token requirement.
        runtime = resolveRuntime();

        ClassLoader[] parentLoaders = loaders.toArray(new ClassLoader[0]);

        // Interpreter mode: the runtime serves classes and resources from
        // source form; nothing is materialized or cached.
        ClassLoader sourceLoader = runtime.createSourceLoader(this, computeClasspath(), parentLoaders);
        if (sourceLoader != null) {
            Util.err.log(Level.FINE, "source module {0} interpreted by {1}", new Object[] {this, runtime.getId()});
            classloader = new RuntimeModuleClassLoader(sourceLoader, parentLoaders);
            return;
        }

        // Bytecode mode: snapshot all manager-dependent compile inputs
        // HERE (under the write mutex) so that ensureCompiled, invoked from
        // arbitrary lock contexts, never needs the manager. Source-module
        // dependencies are recorded as module objects and their fixed cache
        // directories; they are compiled on demand at first use.
        LinkedHashSet<File> cp = new LinkedHashSet<>();
        List<SourceModule> sourceDeps = new ArrayList<>();
        // Own patches and Class-Path extensions (but not the source JAR itself).
        List<File> loaderPrefix = new ArrayList<>();
        for (File f : getAllJars()) {
            if (!f.equals(getJarFile())) {
                cp.add(f);
                loaderPrefix.add(f);
            }
        }
        collectDependencyJars(this, cp, sourceDeps, new HashSet<String>());
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
        snapshotClasspath = new ArrayList<>(cp);
        snapshotSourceDeps = sourceDeps;
        // Loader file list template: patches/extensions + fragment injections
        // around the compiled output (refineModulePath may append fragment
        // JARs; data().addCp contributes the Class-Path extensions).
        List<File> loaderSuffix = new ArrayList<>();
        ((StandardModuleData)data()).addCp(loaderSuffix);
        getManager().refineModulePath(this, loaderSuffix);
        snapshotLoaderPrefix = loaderPrefix;
        snapshotLoaderSuffix = loaderSuffix;
        moduleParents = loaders;

        // Runtimes that support lazy compilation (the Java one) get a
        // lazy loader: enablement only creates the loader, the build
        // happens on first use (or right here in fail-fast mode). Other
        // runtimes may generate arbitrary output the lazy loader cannot
        // classify, so they compile eagerly at enablement and use the
        // ordinary module classloader.
        realLoader = null;
        compiledClasses = null;
        compileFailure = null;
        if (runtime.supportsLazyCompilation()) {
            classloader = new LazySourceModuleClassLoader(parentLoaders);
            if (Boolean.getBoolean(EAGER_PROP)) {
                ensureCompiled();
            }
        } else {
            ensureCompiled();
            classloader = realLoader;
        }
    }

    /**
     * Resolves the source runtime requested by this module's manifest.
     * Runtimes are backed by an enabled module, so the manifest must
     * require the matching token - otherwise the module system has no
     * reason to enable the runtime first.
     */
    private SourceRuntime resolveRuntime() throws IOException {
        String id = runtimeId(getManifest());
        String token = SourceRuntime.TOKEN_PREFIX + id;
        String requires = getManifest().getMainAttributes().getValue("OpenIDE-Module-Requires"); // NOI18N
        boolean required = requires != null;
        if (required) {
            required = false;
            for (String tok : requires.split("\\s*,\\s*")) { // NOI18N
                if (tok.equals(token)) {
                    required = true;
                    break;
                }
            }
        }
        if (!required) {
            throw new IOException(
                "Source module " + getCodeNameBase() + " uses runtime '" + id + "' " // NOI18N
                + "but does not require the token " + token + ". " // NOI18N
                + "Declare OpenIDE-Module-Requires: " + token // NOI18N
                + " and have a module providing the token (OpenIDE-Module-Provides: " + token + ")."); // NOI18N
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
        collectDependencyJars(this, cp, null, new HashSet<String>());
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

    /**
     * The fixed on-disk location of a source module's compiled output. The
     * directory is created on demand by ensureCompiled; recording the path
     * in advance lets the snapshot stay manager-free (the module object
     * forces compilation at use time).
     */
    static File fixedClassesDir(String codeNameBase) {
        return new File(Places.getCacheSubdirectory("source-modules/" + codeNameBase.replace('.', '-')), // NOI18N
                "classes"); // NOI18N
    }

    /**
     * Adds JARs (or compiled-output dirs) of all transitive module
     * dependencies. Source-module dependencies are recorded as module
     * objects in {@code sourceDeps} (may be null when the caller does not
     * need forcing, e.g. the interpreter path) and contribute their fixed
     * cache directory; they are compiled on demand by ensureCompiled.
     */
    private void collectDependencyJars(Module m, Set<File> cp, List<SourceModule> sourceDeps, Set<String> visited) {
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
            // Only enabled modules contribute: the enabled dependency
            // closure is manifest-determined and therefore stable across
            // runs (disabled platform variants must not leak into the
            // compile classpath, or the cache stamp would churn).
            if (!parent.isEnabled()) {
                continue;
            }
            if (parent instanceof SourceModule) {
                // A source module: compile against its compiled output
                // (created on demand - laziness propagates), plus its
                // patches and Class-Path extensions.
                SourceModule sm = (SourceModule) parent;
                cp.add(fixedClassesDir(sm.getCodeNameBase()));
                if (sourceDeps != null && !sourceDeps.contains(sm)) {
                    sourceDeps.add(sm);
                }
                for (File f : parent.getAllJars()) {
                    if (!f.equals(parent.getJarFile())) {
                        cp.add(f);
                    }
                }
            } else {
                cp.addAll(parent.getAllJars());
            }
            // A fragment carries no classes of its own; its host's JARs
            // (including Class-Path extensions) provide them, e.g. the
            // javac API jars for lib.nbjavac/lib.javacapi.
            String fragmentHost = null;
            try {
                Manifest parentManifest = parent.getManifest();
                if (parentManifest != null) {
                    fragmentHost = parentManifest.getMainAttributes().getValue("OpenIDE-Module-Fragment-Host"); // NOI18N
                }
            } catch (RuntimeException ex) {
                Util.err.log(Level.FINE, "Cannot read manifest of {0}", parent); // NOI18N
            }
            if (fragmentHost != null) {
                String hostName = (String) Util.parseCodeName(fragmentHost)[0];
                Module host = getManager().get(hostName);
                if (host != null && host.isEnabled() && visited.add(hostName)) {
                    cp.addAll(host.getAllJars());
                    collectDependencyJars(host, cp, sourceDeps, visited);
                }
            }
            // Conversely, a host's classloader merges its fragments' JARs
            // (including their Class-Path extensions), e.g. the nb-javac
            // API jars attached to libs.javacapi - but only the fragments
            // that are actually enabled (platform variants!).
            for (Module frag : getManager().getAttachedFragments(parent)) {
                if (!frag.isEnabled()) {
                    continue;
                }
                if (visited.add(frag.getCodeNameBase())) {
                    cp.addAll(frag.getAllJars());
                    collectDependencyJars(frag, cp, sourceDeps, visited);
                }
            }
            collectDependencyJars(parent, cp, sourceDeps, visited);
        }
    }

    @Override
    public void reload() throws IOException {
        // The recompile-on-use machinery will pick up any changes:
        // the stamp check in SourceModuleCompiler compares against the
        // source JAR contents afresh on every compilation.
        super.reload();
    }

    public @Override String toString() {
        String s = "SourceModule:" + getCodeNameBase() + " jarFile: " + getJarFile(); // NOI18N
        if (!isValid()) s += "[invalid]"; // NOI18N
        return s;
    }

    // ---------------------------------------------------------------------
    // Resource laziness support
    // ---------------------------------------------------------------------

    /** Whether the resource is (or may be) produced by annotation processing,
     *  and may therefore have a build-time shipped copy worth serving while
     *  the module is not yet compiled. */
    static boolean isGeneratedResource(String name) {
        return GENERATED_LAYER_ENTRY.equals(name)
                || name.endsWith("Bundle.properties") // NOI18N
                || name.startsWith("META-INF/services/") // NOI18N
                || name.startsWith("META-INF/namedservices/") // NOI18N
                || name.equals("META-INF/namedservices.index"); // NOI18N
    }

    /**
     * Whether a hybrid JAR entry belongs to the generated namespace that must
     * never feed the runtime self-compile: the processors either refuse to
     * regenerate ("already exists in sources") or would merge a stale copy.
     * Note Bundle.properties is NOT here: a handwritten bundle is a genuine
     * source input, and merging a pre-merged copy is idempotent - so it is
     * extracted and re-merged, and its shipped copy is only served as a
     * pre-compile convenience.
     */
    static boolean isGeneratedNamespace(String name) {
        return GENERATED_LAYER_ENTRY.equals(name)
                || LAYER_HINT_ENTRY.equals(name)
                || name.startsWith("META-INF/services/") // NOI18N
                || name.startsWith("META-INF/namedservices/") // NOI18N
                || name.equals("META-INF/namedservices.index"); // NOI18N
    }

    /**
     * Class loader for bytecode-mode source modules. It answers from the
     * source JAR where possible (the compile would copy those entries
     * verbatim) and compiles the module on everything else - so an enabled
     * but unused module never pays for compilation.
     */
    private final class LazySourceModuleClassLoader extends ProxyClassLoader implements Util.ModuleProvider {

        /** Entry names present in the source JAR (no directories, no classes, no manifest). */
        private final Set<String> jarEntries;
        /** Cached answer of the build-time layer hint, or null if unread. */
        private String layerHint;

        LazySourceModuleClassLoader(ClassLoader[] parents) throws IOException {
            super(parents, false);
            Set<String> entries = new HashSet<>();
            Set<String> packages = new HashSet<>();
            try (JarFile jf = new JarFile(getJarFile())) {
                Enumeration<JarEntry> en = jf.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (n.endsWith("/") || n.equals("META-INF/MANIFEST.MF") || n.endsWith(".class")) { // NOI18N
                        continue;
                    }
                    if (n.startsWith(SOURCE_BUNDLES_PREFIX)) {
                        // build metadata (handwritten bundle copies for the
                        // runtime compile's source tree), not a resource
                        continue;
                    }
                    entries.add(n);
                    int slash = n.lastIndexOf('/');
                    if (slash > 0) {
                        packages.add(n.substring(0, slash).replace('/', '.'));
                    }
                }
            }
            // Class-Path extension JARs (and patches) are static knowledge:
            // cover their packages right away, so reference resolution from
            // dependent modules routes here (triggering the lazy compile)
            // instead of falling through to the system classloader.
            for (File f : getAllJars()) {
                if (f.equals(getJarFile())) {
                    continue;
                }
                if (f.isDirectory()) {
                    collectPackages(f, "", packages);
                } else {
                    collectJarPackages(f, packages);
                }
            }
            // Service registrations do not exist in the source JAR (they are
            // generated), but lookups for them must route here to trigger
            // the lazy compile; the compiled output adds exact coverage for
            // the rest (e.g. nested namedservices) once it exists.
            packages.add("META-INF.services"); // NOI18N
            packages.add("META-INF.namedservices"); // NOI18N
            jarEntries = entries;
            Util.err.log(Level.FINE, "lazy loader {0} covering packages {1}", new Object[] {getCodeNameBase(), packages});
            addCoveredPackages(packages);
        }

        /** Packages of a JAR's contents - class files included, since a
         *  library JAR typically contains nothing else. */
        private static void collectJarPackages(File jar, Set<String> out) {
            try (JarFile jf = new JarFile(jar)) {
                Enumeration<JarEntry> en = jf.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (n.endsWith("/")) { // NOI18N
                        continue;
                    }
                    int slash = n.lastIndexOf('/');
                    if (slash > 0) {
                        out.add(n.substring(0, slash).replace('/', '.'));
                    }
                }
            } catch (IOException ex) {
                Util.err.log(Level.FINE, "Cannot enumerate packages of {0}", jar); // NOI18N
            }
        }

        /** Extends coverage with the packages of the compiled output and
         *  Class-Path extensions, so references resolved through the inner
         *  loader (ext-jar packages in particular) route back here. */
        void coverClasspath(List<File> classp) {
            Set<String> packages = new HashSet<>();
            for (File f : classp) {
                if (f.isDirectory()) {
                    collectPackages(f, "", packages);
                } else if (f.isFile() && f.getName().endsWith(".jar")) { // NOI18N
                    collectJarPackages(f, packages);
                }
            }
            addCoveredPackages(packages);
        }

        private static void collectPackages(File dir, String prefix, Set<String> out) {
            File[] kids = dir.listFiles();
            if (kids == null) {
                return;
            }
            for (File kid : kids) {
                if (kid.isDirectory()) {
                    collectPackages(kid, prefix + kid.getName() + "/", out); // NOI18N
                } else {
                    String n = prefix + kid.getName();
                    int slash = n.lastIndexOf('/');
                    if (slash > 0) {
                        out.add(n.substring(0, slash).replace('/', '.'));
                    }
                }
            }
        }

        public @Override Module getModule() {
            return SourceModule.this;
        }

        /** Classes always trigger the lazy compile. */
        protected @Override Class<?> doLoadClass(String pkg, String name) {
            try {
                ensureCompiled();
                return realLoader().loadClass(name);
            } catch (IOException | ClassNotFoundException ex) {
                return null;
            }
        }

        public @Override URL findResource(String name) {
            if (isGeneratedResource(name)) {
                // Generated artifacts (the layer, service registrations,
                // merged bundles) are shipped in hybrid JARs - derived from
                // the same sources the runtime would compile, invalidated
                // together with them by the staleness stamp - so serve the
                // shipped copy while uncompiled (the layer is eagerly looked
                // up for every enabled module at startup). Once compiled,
                // the regenerated copies are authoritative, with the shipped
                // copy as fallback (the compile does not regenerate
                // handwritten registrations). Modules that ship none (pure
                // modules) compile on first lookup, and the layer hint lets
                // layer-less modules answer without compiling at all.
                if (compiledClasses == null && jarEntries.contains(name)) {
                    return jarUrl(name);
                }
                if (compiledClasses == null && GENERATED_LAYER_ENTRY.equals(name)
                        && "layer=false".equals(layerHint())) { // NOI18N
                    return null;
                }
                try {
                    ensureCompiled();
                    URL url = realLoader().getResource(name);
                    return url != null ? url : jarUrl(name);
                } catch (IOException ex) {
                    return jarEntries.contains(name) ? jarUrl(name) : null;
                }
            }
            // Present in the source JAR? Serve it verbatim - the compile
            // would copy the same bytes into its output anyway.
            if (jarEntries.contains(name)) {
                return jarUrl(name);
            }
            return null;
        }

        public @Override Enumeration<URL> findResources(String name) throws IOException {
            if (GENERATED_LAYER_ENTRY.equals(name) && "layer=false".equals(layerHint()) // NOI18N
                    && compiledClasses == null) {
                return java.util.Collections.emptyEnumeration();
            }
            if (isGeneratedResource(name)) {
                if (compiledClasses == null && jarEntries.contains(name)) {
                    return java.util.Collections.enumeration(java.util.Collections.singleton(jarUrl(name)));
                }
                ensureCompiled();
                // The compile regenerates generated registrations but not
                // handwritten ones; those must remain visible via the
                // shipped copy. Prefer the compiled output, fall back to
                // (do not merge with) the shipped copy - one source of
                // truth, no duplicates.
                Enumeration<URL> fromOutput = realLoader().getResources(name);
                if (fromOutput.hasMoreElements()) {
                    return fromOutput;
                }
                return jarEntries.contains(name)
                        ? java.util.Collections.enumeration(java.util.Collections.singleton(jarUrl(name)))
                        : java.util.Collections.<URL>emptyEnumeration();
            }
            if (jarEntries.contains(name)) {
                return java.util.Collections.enumeration(java.util.Collections.singleton(jarUrl(name)));
            }
            return java.util.Collections.emptyEnumeration();
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
            // Same package-export/friend/hidden-JRE-package policy as
            // ordinary module loaders (#164510 etc.).
            return getManager().shouldDelegateResource(SourceModule.this, other, pkg, parent);
        }

        public @Override void destroy() {
            super.destroy();
            ClassLoader rl = realLoader;
            if (rl instanceof ProxyClassLoader pcl) {
                pcl.destroy();
            }
        }

        /** Reads the build-time layer hint ("layer=true"/"false"/"unknown"). */
        private String layerHint() {
            if (layerHint == null) {
                layerHint = "layer=unknown"; // NOI18N
                if (jarEntries.contains(LAYER_HINT_ENTRY)) {
                    try (JarFile jf = new JarFile(getJarFile())) {
                        InputStream is = jf.getInputStream(jf.getEntry(LAYER_HINT_ENTRY));
                        if (is != null) {
                            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                                byte[] b = new byte[256];
                                int len;
                                while ((len = is.read(b)) != -1) {
                                    baos.write(b, 0, len);
                                }
                                layerHint = baos.toString("UTF-8").trim(); // NOI18N
                            }
                        }
                    } catch (IOException ex) {
                        Util.err.log(Level.FINE, "Cannot read {0} of {1}", // NOI18N
                                new Object[] {LAYER_HINT_ENTRY, getJarFile()});
                    }
                }
            }
            return layerHint;
        }

        private URL jarUrl(String name) {
            try {
                return new URL("jar:" + getJarFile().toURI() + "!/" + name); // NOI18N
            } catch (java.net.MalformedURLException ex) {
                return null;
            }
        }

        public @Override String toString() {
            return "LazySourceModuleCL@" + Integer.toHexString(System.identityHashCode(this)) // NOI18N
                    + "[" + getCodeNameBase() + (compiledClasses != null ? "" : ",uncompiled") + "]"; // NOI18N
        }
    }

    /** Direct access for the loader inner classes. */
    ClassLoader realLoader() {
        return realLoader;
    }

    /**
     * Class loader for interpreter-mode source modules: a standard
     * multi-parented module classloader (package coverage, resource
     * delegation policy, module identity) whose "own"
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

        public @Override void destroy() {
            super.destroy();
            if (delegate instanceof ProxyClassLoader pcl) {
                pcl.destroy();
            }
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
        try (JarFile jf = new JarFile(getJarFile())) {
            Enumeration<JarEntry> en = jf.entries();
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
            Util.err.log(Level.WARNING, "Cannot enumerate packages of " + getJarFile(), ex);
        }
        return pkgs;
    }

}
