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

package org.netbeans.modules.source.groovy;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.netbeans.Module;
import org.netbeans.SourceRuntime;

/**
 * The Groovy source runtime: the simplest form of a {@link SourceRuntime},
 * interpreter mode. A module with {@code OpenIDE-Module-Source: groovy} and
 * the {@code org.netbeans.modules.source.runtime.groovy} token requirement
 * ships Groovy sources in place of classes; they are defined in memory on
 * first use by {@link GroovyClassLoader} - nothing is ever compiled to or
 * loaded from disk, the source JAR is the entire deployment artifact.
 * <p>
 * The module system wraps the returned loader with the full module
 * classloader semantics (parent modules, package coverage, resource
 * delegation policy), so this class only has to care about Groovy:
 * serve the module's classes from its source JAR, compile against its
 * declared dependencies.
 *
 * @see SourceRuntime
 */
public class GroovySourceRuntime extends SourceRuntime {

    public @Override String getId() {
        return "groovy"; // NOI18N
    }

    public @Override String getVersion() {
        return GroovySystem.getVersion();
    }

    public @Override ClassLoader createSourceLoader(Module m, List<File> classpath, ClassLoader[] parents) throws IOException {
        GroovyClassLoader gcl = new GroovyClassLoader(parents.length == 0 ? null : parents[0]);
        // the module's .groovy sources:
        gcl.addClasspath(m.getJarFile().toURI().toURL().toString());
        // its declared dependencies (JARs or compiled source-module outputs):
        for (File f : classpath) {
            gcl.addClasspath(f.toURI().toString());
        }
        return gcl;
    }

}
