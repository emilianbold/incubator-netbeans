# Source-only NetBeans modules

This is an experimental feature that lets NetBeans modules be shipped as
**source code** and compiled **on demand, at runtime**, by the module system.

## Motivation

The module system (and the OSGi bridge) already loads modules dynamically.
If the JVM we run on is a JDK, it contains a full Java compiler - so the
"binary" form of a module is not fundamentally necessary. Shipping sources
instead of class files has interesting properties:

* A locally patched module is detected as **stale** and transparently
  recompiled on the next start - no build system required to hack on a
  running IDE.
* There is exactly one artifact to keep in sync - the source JAR can never
  diverge from the sources it was built from.
* Distribution is JDK/bytecode-version agnostic: the module is always
  compiled by the JDK that runs it.

## How it works

### Packaging format

A source-only module is an ordinary module JAR with the same layout a binary
module has - complete manifest, resources at the root - except that it holds
`*.java` files where a binary module holds compiled classes. The manifest
must carry one extra header:

    OpenIDE-Module-Source: true

Two variants exist:

* **Pure** - sources + resources only, no classes.
* **Hybrid** (transition form) - compiled classes *plus* the source tree.
  The runtime ignores the packaged classes entirely and always compiles from
  the sources; the classes are only there so that *not yet converted*
  modules which depend on this one can still be compiled at build time.

### Runtime (platform/o.n.bootstrap)

* `ModuleFactory.create` inspects the manifest of every module JAR it is
  asked to create. If it sees `OpenIDE-Module-Source: true` it creates a
  `SourceModule` instead of a `StandardModule`.
* `SourceModule.classLoaderUp` (i.e. when the module is enabled) calls
  `SourceModuleCompiler.compileIfNeeded`, then builds the module
  classloader over the **compiled output directory** (`JarClassLoader` has
  always supported directories - the same mechanism used by
  `netbeans.patches.*`). Patches keep precedence, `Class-Path` extension
  JARs keep working, the source JAR itself is deliberately *not* on the
  classpath (so resources are never seen twice).
* The compilation classpath mirrors what the ant harness would use: the
  JARs of all declared module dependencies (transitively, including their
  patches and `Class-Path` extensions), the **compiled output directory**
  for dependencies that are themselves source modules (dependencies are
  always enabled before dependents, so those are already compiled), and
  the JARs of the fixed (boot) modules - the latter have no JAR list of
  their own, so their classloaders' visible `META-INF/MANIFEST.MF` JARs
  are enumerated (in a regular startup this is `MainImpl`'s
  `BootClassLoader`, covering both platform `lib/` and `core/`).
* Compilation runs **in-process** via `javax.tools` (
  `ToolProvider.getSystemJavaCompiler()`), so NetBeans must be executed on
  a JDK, not a bare JRE. (Using nb-javac instead is a possible future
  refinement.)
* **Annotation processing is fully reproduced.** All the "build-time magic"
  of NetBeans modules - `@ServiceProvider` registrations, generated
  `META-INF/generated-layer.xml`, `@NbBundle.Messages` bundle merging,
  `@MIMEResolver`, `@ActionRegistration`, `@TemplateRegistration`, ... -
  is plain JSR-269 processing performed by processors that are shipped
  *inside the dependency module JARs themselves*. The runtime compile uses
  the very same jars as `-processorpath` (with `-proc:full` and no `-A`
  options, exactly like the harness), so the output is structurally
  identical to a build-time-built module. The runtime layer loader picks up
  `META-INF/generated-layer.xml` as a classloader resource, no manifest
  entry needed.
* **Caching and staleness.** Compiled output is cached in
  `var/cache/source-modules/<code-name-base>/`. A stamp file records a
  digest of the source JAR *contents* (skipping the manifest's volatile
  build-stamp attributes and any packaged classes of hybrid modules), the
  size/mtime of every classpath entry, and the JDK version. Any change -
  e.g. the user patched the JAR locally, a dependency was rebuilt, or the
  JDK changed - invalidates the cache and triggers recompilation.
  Recompilation is all-or-nothing: the stamp is written only after a
  successful compile, and a failed compile leaves no partial output behind.
* **Failure reporting.** A broken source module behaves like any other
  broken module: enablement fails with an `InvalidException` carrying the
  compiler diagnostics, and the module shows up in the module system with
  problems.

### Build harness (nbbuild/templates/common.xml)

A module opts in via `nbproject/project.properties`:

    is.source.module=true

plus the `OpenIDE-Module-Source: true` header in `manifest.mf`. By default
it is then packaged in **hybrid** form: compiled classes *minus the
regenerable artifacts* (generated service registrations, merged
`Bundle.properties`, generated layer - these would collide with the
annotation processors when recompiled at runtime, and are regenerated
anyway), plus the source tree and any handwritten registrations/bundles.
The runtime ignores all packaged classes and always compiles from source.
Once no binary module depends on this one anymore - it is a leaf of the
module dependency graph, or all of its dependents have been converted - it
can switch to pure form:

    source.module.pure=true

which skips compilation entirely at build time and packages only sources +
resources. `source.module.compile=true` forces build-time compilation for
verification even in pure mode (classes are never packaged in pure mode).

Cluster builds need no ordering changes: pure modules compile nothing at
build time, and every runtime dependency (binary or source) is present in
the built cluster.

### Converting a module (recipe)

1. `manifest.mf`: add `OpenIDE-Module-Source: true`.
2. `nbproject/project.properties`: add `is.source.module=true`.
3. Rebuild. Done - the runtime does the rest.
4. (Later, when safe: add `source.module.pure=true`.)

## Which modules can be converted

Good candidates (the vast majority of the codebase): modules built by the
standard harness targets - `javac` + JSR-269 annotation processing +
resource copying. No `-A` options, no extra ant tasks needed.

Modules that need extra care or should stay binary:

* **Boot-classpath modules** (`lib/*.jar`, `core/*.jar`: `o.n.bootstrap`,
  `openide.util*`, `openide.filesystems`, `openide.modules`, `core.startup`,
  ...) - they are loaded as fixed modules from the startup classloader,
  before the module system exists; and the whole platform compiles and
  processes annotations against them.
* **Annotation-processor providers** (`openide.util.lookup`,
  `api.annotations.common`, `openide.awt`, `openide.filesystems`,
  `settings`, `editor.mimelookup`, ...) must stay **binary**: their
  generated `META-INF/services/javax.annotation.processing.Processor`
  registrations have to remain in their JARs so that other modules' *build
  time* annotation processing keeps working. (Their runtime compiled output
  could serve as the processor path for dependent *source* modules, which
  would make even these convertible - but only together with all their
  users, and they are load-bearing for every compile: convert them last.)
* **Modules with native libraries / pre-built binaries** (`dlight.nativeexecution`,
  `keyring`, JNA wrappers, ...), **JavaHelp** modules (jhindexer builds a
  separate search index JAR), and modules with real **source preprocessing**
  or **code generation** (`web.jspparser`'s servlet-API rewriting, JavaCC/
  ANTLR grammars, ant-task compilation into `modules/ext`) - keep them
  binary, or teach the harness to run those steps at runtime.
* **OSGi bundles** consumed through the netigso bridge are foreign artifacts
  anyway.

## Rollout plan (towards "most modules are source")

Conversion must proceed **from the leaves of the dependency graph upwards**:
a module may drop its packaged classes (pure form) only when nothing that is
still binary depends on it. Hybrid form is the bridge that makes the
transition monotonic - at any point the build stays green, and at runtime
*every* converted module is already exercised through the compile-on-demand
path regardless of hybrid/pure packaging.

Suggested stages:

1. (done, demo) `spi.actions`, `htmlui` pure; `openide.dialogs`,
   `openide.explorer`, `openide.actions`, `api.progress` hybrid - the last
   four form a dependency chain, so the IDE start exercises source-on-source
   compilation. Verified live on a real platform start: all four compile
   from source during startup (regenerating layers and service
   registrations via the runtime annotation processors), and a warm start
   on the same userdir reuses the cache with no recompilation at all.
2. Leaves and low-fan-in modules across all clusters (analysis script over
   `*/nbproject/project.xml`), hybrid first, pure when their remaining
   dependents are converted.
3. Whole subsystems once their internal dependency subtree is fully
   converted.
4. The small binary core that remains: bootstrap, the `openide.*` SPI
   modules, processor-provider modules, native-code and javahelp modules -
   plus anything with unusual build steps.

## Limitations / future work

* NetBeans must run on a JDK (in-process `javax.tools`). A fallback to
  nb-javac (`java/lib.nbjavac`, `java/libs.nbjavacapi`), which the IDE ships
  anyway for the Java editing model, would remove even that requirement.
* Localization variant JARs (`locale/*_cs.jar`) are not handled for source
  modules yet (sources are compiled base-locale only).
* Runtime compilation is serialized by the module manager mutex; a future
  optimization could compile independent modules in parallel.
* The consistency verifier (`Asm`-based class linkage checks) only sees
  packaged classes; source-module linkage is verified implicitly by the
  compiler itself.
* Module unit tests (`ant test-unit`) still need binary classes - run them
  with `-Dsource.module.compile=true` (hybrid modules always compile).
* First-start cost: a fresh user dir compiles all enabled source modules
  once (cached afterwards). Parallel compilation and a shared system-wide
  precompiled cache are natural follow-ups.

## Pointers

* Runtime: `platform/o.n.bootstrap/src/org/netbeans/SourceModule.java`,
  `SourceModuleCompiler.java`, `ModuleFactory.java`,
  `StandardModule.java` (`findPatches` visibility).
* Tests: `platform/o.n.bootstrap/test/unit/src/org/netbeans/SourceModuleTest.java`.
* Harness: `nbbuild/templates/common.xml` (`-do-jar-source-pure`,
  `-do-jar-source-hybrid`, `-check-compile`).
