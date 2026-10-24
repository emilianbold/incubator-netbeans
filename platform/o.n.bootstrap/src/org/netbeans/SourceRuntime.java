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
import java.util.List;

/**
 * A source runtime: the thing that turns a source-only module's sources
 * into something loadable. The Java runtime is built into the Platform
 * (a module with {@code OpenIDE-Module-Source: true} or {@code java}
 * needs nothing but the JDK the IDE runs on); runtimes for other source
 * languages (bytecode-producing or interpreted) are provided by regular
 * modules.
 * <p>
 * A module declares which runtime it needs with the manifest header
 *
 * <pre>OpenIDE-Module-Source: &lt;runtime id&gt;</pre>
 *
 * For any id other than {@code java} the module must also declare the
 * matching token requirement, so that the module system enables the
 * runtime provider before the module itself:
 *
 * <pre>OpenIDE-Module-Requires: org.netbeans.modules.source.runtime.&lt;runtime id&gt;</pre>
 *
 * and the provider module declares
 *
 * <pre>OpenIDE-Module-Provides: org.netbeans.modules.source.runtime.&lt;runtime id&gt;</pre>
 *
 * and registers its implementation in
 * {@code META-INF/services/org.netbeans.SourceRuntime} (the implementation
 * should live in a public package of the provider module).
 * <p>
 * The module system takes care of everything language-agnostic: caching
 * and staleness detection (including the runtime {@link #getVersion}),
 * extraction, classloader construction with full module semantics,
 * patches and failure reporting.
 * <p>
 * Two integration modes exist; a runtime picks one:
 * <ul>
 * <li><b>Interpreter mode</b> (the purest form): {@link #createSourceLoader}
 *     returns a loader that serves classes and resources directly from
 *     source form. Nothing is materialized, nothing is cached - the
 *     module JAR of plain source code is the entire deployment artifact.
 * <li><b>Bytecode mode</b>: {@link #build} produces class files and
 *     resources in an output directory. This buys a compile-once cache
 *     and staleness-based recompilation, at the cost of writing bytecode
 *     to disk.
 * </ul>
 *
 * @see SourceModule
 * @since org.netbeans.base/1
 */
public abstract class SourceRuntime {

    /** Prefix of the token a runtime provider module must provide. */
    public static final String TOKEN_PREFIX = "org.netbeans.modules.source.runtime."; // NOI18N

    /** Name of the service file runtime providers register through. */
    static final String SERVICE_FILE = "META-INF/services/org.netbeans.SourceRuntime"; // NOI18N

    /** Identity of this runtime, e.g. {@code java} or {@code clojure}. */
    public abstract String getId();

    /**
     * The token a provider of this runtime declares in
     * {@code OpenIDE-Module-Provides}; also the token a module using this
     * runtime must require.
     */
    public final String getToken() {
        return TOKEN_PREFIX + getId();
    }

    /**
     * Version of the compiler/runtime, included in the staleness stamp:
     * changing it invalidates cached module compilations.
     */
    public abstract String getVersion();

    /**
     * Builds the module in bytecode mode: turns the extracted sources into
     * class files and resources in the (initially empty) output directory,
     * which the module classloader then loads - with caching and staleness
     * detection. Only runtimes that produce loadable output on disk need
     * to implement this.
     *
     * @param srcDir the module's source JAR contents, extracted (minus
     *        the manifest and any packaged classes)
     * @param outputDir the directory the module classloader will load
     *        from; class files and resources in ordinary module layout
     * @param classpath files (JARs or directories) to compile against,
     *        mirroring the compile classpath the ant harness would use
     * @throws IOException on failure; the message should carry useful
     *         diagnostics - it becomes the module's problem description
     */
    public void build(File srcDir, File outputDir, List<File> classpath) throws IOException {
        throw new IOException(
            "Source runtime '" + getId() + "' does not support bytecode materialization"); // NOI18N
    }

    /**
     * Interpreter-mode runtimes override this instead of {@link #build}:
     * provide a class loader that serves the module's classes and
     * resources directly from source form - interpreting, or compiling in
     * memory. Nothing is written to disk and nothing is cached; the
     * module's JAR <em>is</em> the deployment artifact, plain source code.
     * <p>
     * The module system wraps the returned loader with the full module
     * classloader semantics (parent modules, permissions, package
     * coverage, resource delegation policy), so implementors only have to
     * worry about their language. The loader should serve the module's
     * resources from its source JAR ({@link Module#getJarFile}) - e.g.
     * handwritten {@code META-INF/services} registrations and layers work
     * unchanged - and answer {@code loadClass} for the module's own
     * classes, compiling or interpreting them on demand.
     * <p>
     * The default implementation returns {@code null}, which selects
     * bytecode mode: sources are extracted, {@link #build} is called and
     * the produced output directory is loaded, cached and staleness-checked.
     *
     * @param m the module being enabled; its sources are in
     *        {@link Module#getJarFile}
     * @param classpath files (JARs or directories) to compile/load
     *        against, mirroring the compile classpath the ant harness
     *        would use
     * @param parents the parent classloaders of the module (module
     *        dependencies and the boot classloader), for the runtime to
     *        compile against if it wants to
     * @return the loader serving the module from source form, or
     *         {@code null} to select bytecode mode
     * @throws IOException on failure; the message becomes the module's
     *         problem description
     */
    public ClassLoader createSourceLoader(Module m, List<File> classpath, ClassLoader[] parents) throws IOException {
        return null;
    }

    /**
     * Finds the source runtime for the given manifest header value.
     * {@code null}, {@code true} and {@code java} yield the built-in Java
     * runtime; anything else is looked up among <em>enabled</em> modules
     * that provide the matching token and register a SourceRuntime
     * service. Callers must ensure the runtime module is enabled first
     * (normally by the requiring module declaring the token requirement).
     *
     * @param mgr the module manager to search
     * @param id the {@code OpenIDE-Module-Source} header value
     * @return the runtime
     * @throws IOException if no enabled module provides a runtime with
     *         that id
     */
    public static SourceRuntime find(ModuleManager mgr, String id) throws IOException {
        if (id == null || "true".equalsIgnoreCase(id) || "java".equals(id)) { // NOI18N
            return JavaSourceRuntime.getDefault();
        }
        String token = TOKEN_PREFIX + id;
        for (Module m : mgr.getModules()) {
            if (!m.isEnabled() || !m.provides(token)) {
                continue;
            }
            ClassLoader loader = m.getClassLoader();
            java.util.Enumeration<java.net.URL> en = loader.getResources(SERVICE_FILE);
            while (en.hasMoreElements()) {
                for (String line : readServiceFile(en.nextElement())) {
                    SourceRuntime rt = instantiate(line, loader);
                    if (rt != null && id.equals(rt.getId())) {
                        return rt;
                    }
                }
            }
        }
        throw new IOException(
            "No enabled module provides source runtime '" + id + "'. " + // NOI18N
            "A module using OpenIDE-Module-Source: " + id + // NOI18N
            " must require the token " + token + // NOI18N
            " and a module providing the token must register an implementation in " + SERVICE_FILE); // NOI18N
    }

    private static SourceRuntime instantiate(String className, ClassLoader loader) {
        try {
            Class<?> c = Class.forName(className, true, loader);
            return (SourceRuntime) c.getDeclaredConstructor().newInstance();
        } catch (ClassCastException ex) {
            Util.err.warning(SERVICE_FILE + " entry is not a SourceRuntime: " + className); // NOI18N
        } catch (ReflectiveOperationException ex) {
            Util.err.log(java.util.logging.Level.WARNING, "Cannot instantiate source runtime " + className, ex); // NOI18N
        }
        return null;
    }

    private static List<String> readServiceFile(java.net.URL u) throws IOException {
        List<String> names = new java.util.ArrayList<>();
        try (java.io.InputStream is = u.openStream();
             java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash != -1) {
                    line = line.substring(0, hash);
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    names.add(line);
                }
            }
        }
        return names;
    }

}
