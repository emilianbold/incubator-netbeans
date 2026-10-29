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
source files where a binary module holds compiled classes. The manifest
must carry one extra header, whose value names the **source runtime**
responsible for compiling/materializing it:

    OpenIDE-Module-Source: true        # (or "java") the built-in Java runtime
    OpenIDE-Module-Source: clojure     # a registered non-Java runtime (see below)

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
  build-stamp attributes and any packaged classes - so hybrid and pure
  forms of the same module stamp identically), and the size/mtime of
  every compile classpath entry, *sorted by path*. The classpath set is
  the module's *enabled* dependency closure, which is manifest-determined
  and therefore identical on every start - disabled platform variants
  (e.g. the foreign-OS JavaFX fragments) are never part of it. Any real
  change - the user patched the JAR locally, a dependency was rebuilt,
  the JDK changed - invalidates the cache and triggers recompilation;
  identical starts reuse it completely. Recompilation is all-or-nothing:
  the stamp is written only after a successful compile, and a failed
  compile leaves no partial output behind.
* **Compilation is lazy.** Module *enablement* only creates the loader; the
  build happens on first use - the first class load, or the first lookup of
  a resource the compile would generate. Resources that physically exist in
  the source JAR (handwritten layers, icons, plain bundles) are served
  straight from the JAR without compiling, since the compile copies them
  verbatim anyway. The one eagerly-queried generated resource - the layer,
  looked up for every enabled module during startup - is covered by a
  build-time hint (`META-INF/generated-layer.hint`, `layer=true/false`),
  so modules without generated layers answer that lookup without compiling
  at all. When a dependent source module is compiled, it forces its
  dependencies' compiles on demand, so laziness propagates down the
  dependency graph and only modules that are actually *used* pay for
  compilation. Set `-Dnetbeans.source.modules.eager=true` for fail-fast
  compilation at enablement instead. (Non-Java bytecode runtimes compile
  eagerly - their generated output is not classifiable by the lazy loader;
  interpreter-mode runtimes are inherently lazy already.)
* **Failure reporting.** In lazy mode a broken module only fails when
  first used (class loads throw `ClassNotFoundException`, a warning with
  the compiler diagnostics is logged once); in eager mode
  (`-Dnetbeans.source.modules.eager=true`) enablement fails with an
  `InvalidException` carrying the diagnostics and the module shows up in
  the module system with problems.

### Build harness (nbbuild/templates/common.xml)

A module opts in via `nbproject/project.properties`:

    is.source.module=true

plus the `OpenIDE-Module-Source: true` header in `manifest.mf`. By default
it is then packaged in **hybrid** form: the complete build output -
compiled classes (as before, so the public-package-jars distillation and
every binary dependent keep working unchanged), resources, and all
generated artifacts (layer, service registrations, processor
registrations, merged bundles) - plus the source tree, and the original
handwritten bundles under a side path
(`META-INF/netbeans/source-bundles/`). The runtime serves the shipped
generated artifacts directly while the module is uncompiled (so
services/bundle lookups no longer force compilation - only class loads
do), and its self-compile takes its source-path bundle from the side
path, exactly like an ant build: the merged copies must never feed the
compile (the bundle processor errors on duplicate keys, and javac's
Filer forbids re-reading a file it has just written via a processor).

Once no binary module depends on this one anymore - it is a leaf of the
module dependency graph, or all of its dependents have been converted - it
can switch to pure form:

    source.module.pure=true

which skips compilation entirely at build time and packages only sources +
resources (no generated artifacts to serve, so such modules compile on
first lookup or class load). `source.module.compile=true` forces
build-time compilation for verification even in pure mode (classes are
never packaged in pure mode).

For whole-distribution packaging there is also a post-build step
(`ant trim-source-modules`) that strips the classes from every source
module JAR of the built clusters, converting the hybrid form to pure in
place after all dependents have been built - the classes exist exactly as
long as the build needs them. Users only ever see the trimmed,
source-only JAR. (The trim rewrites the JARs, which changes their
mtime/length and therefore invalidates developer caches once - a
developer-only transition cost; the shipped artifact starts fresh
anyway.)

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
* **Annotation-processor providers** - the 13 platform ones
  (`openide.awt`, `openide.windows`, `openide.nodes`, `openide.loaders`,
  `settings`, `options.api`, `editor.mimelookup`, ...) are now
  **source-only** via the ship-and-skip treatment: their generated
  processor registration ships in the hybrid JAR (build-time dependents
  keep discovering it), is skipped at the runtime self-compile, and is
  regenerated into the compiled output for dependent source modules.
  Providers in the ide cluster follow the same way once those clusters
  convert. (`org.openide.util.lookup` itself is in the bootstrap core and
  stays binary - it is the seed that regenerates everyone else's
  registrations.)
* **Modules with native libraries / pre-built binaries** (`dlight.nativeexecution`,
  `keyring`, JNA wrappers, ...), **JavaHelp** modules (jhindexer builds a
  separate search index JAR), and modules with real **source preprocessing**
  or **code generation** (`web.jspparser`'s servlet-API rewriting, JavaCC/
  ANTLR grammars, ant-task compilation into `modules/ext`) - keep them
  binary, or teach the harness to run those steps at runtime.
* **Modules compiling against the javac APIs** (the `requires.nb.javac`
  family, 46 modules: `lib.nbjavac`, `java.source.base`, `java.sourceui`,
  `java.editor`, `java.hints`, `refactoring.java`, `java.project.ui`, ...)
  and modules whose build resolves platform jars outside the module
  dependency model (`htmlui` and the JavaFX platform) - all found by the
  live smoke tests, all kept binary. The javac family compiles against the
  nb-javac fork's newer `com.sun.source`/`com.sun.tools.javac` APIs while
  the running JDK's `jdk.compiler` module owns those packages, and the
  classpath cannot shadow module-owned packages; the ant harness handles
  this with `--limit-modules` (SetupLimitModules) hiding `jdk.compiler` -
  replicating that in-process made javac pathologically slow, so these
  modules remain binary for now. `htmlui` would need a per-module
  compiler-arguments manifest header (fed from `javac.compilerargs` by the
  harness) to cover such cases.
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
2. (done, mass conversion) 133 of the 262 Platform+Java modules converted,
   then the 13 platform annotation-processor providers followed once the
   ship-and-skip treatment covered their generated processor
   registrations - platform stands at 77 of its 80 real modules (96%)
   source-only. The permanently-binary remainder: `keyring` (native/JNA),
   `openide.filesystems.compat8` (boot classpath), `htmlui` (JavaFX
   platform plumbing), plus the 9-module bootstrap core. In the Java
   cluster 69 of 140 real modules are source-only, capped by the
   javac-API family; the ide/webcommon/javafx clusters await their
   conversion runs (~190 immediately convertible).
3. (done) The 13 platform processor providers - unlocked not by converting
   users first, but by the ship-and-skip treatment: their generated
   `META-INF/services/javax.annotation.processing.Processor` registration
   ships in the hybrid JAR for build-time dependents, is skipped at the
   runtime self-compile, and is regenerated into the provider's compiled
   output where dependent source modules discover it on their processor
   path. (The one known gap: api.annotations.common's HANDWRITTEN
   registration is not regenerated, so its validation-only processor is
   skipped in runtime compiles - benign.) The remaining providers in
   ide/extide clusters follow the same way.
4. Whole subsystems once their internal dependency subtree is fully
   converted.
5. The small binary core that remains: bootstrap, the `openide.*` SPI
   modules, processor-provider modules, native-code and javahelp modules -
   plus anything with unusual build steps.

## Limitations / future work

* NetBeans must run on a JDK (in-process `javax.tools`). A fallback to
  nb-javac (`java/lib.nbjavac`, `java/libs.nbjavacapi`), which the IDE ships
  anyway for the Java editing model, would remove even that requirement.
* Runtime compilation happens outside the module manager's mutex (a
  dedicated `compileLock` per module; dependency compiles acquire locks
  following the - acyclic - dependency DAG), so module-system writers are
  never blocked behind a compile, and first-use compiles from classloading
  threads cannot deadlock against enablement. A side effect is that two
  independent modules may compile concurrently - safe, since their cache
  directories are disjoint.
* The runtime compiler compiles with the JDK the IDE runs on; it cannot
  honor `javac.release` / `javac.compilerargs` (they live in
  `nbproject/project.properties`, which is not packaged). Audited all
  144 converted modules: none use semantics-changing arguments; the only
  settings present are `-Xlint` variants (warnings only, bytecode
  identical), `-Werror` (build-time quality gate, not enforced at
  runtime) and one `javac.release=8` pin (which still generates the
  manifest's `OpenIDE-Module-Java-Dependencies` floor during jarring -
  the enforced runtime compatibility claim; the bytecode-floor aspect is
  moot since no bytecode ships). The harness now refuses
  (`-source-module-settings-check` in common.xml) to package a source
  module whose compiler arguments would change runtime semantics:
  `-A` processor options, `--add-exports/opens/reads`, `-processor`,
  `-proc:` modes, `-XD` internals, `--enable-preview`.
* Localization variant JARs (`locale/*_cs.jar`) are not handled for source
  modules yet (sources are compiled base-locale only).
* Module unit tests (`ant test-unit`) still need binary classes - run them
  with `-Dsource.module.compile=true` (hybrid modules always compile).
* First-start cost: a fresh user dir compiles all enabled source modules
  once (cached afterwards). Parallel compilation and a shared system-wide
  precompiled cache are natural follow-ups.

## Generalization: source runtimes for other languages

Nothing in the design is Java-specific: the module system only needs
*somebody* to turn a module's source form into something loadable, plus
the usual manifest/dependencies/layers. The `OpenIDE-Module-Source` header
therefore carries the id of the **source runtime** responsible for the
module:

    OpenIDE-Module-Source: true        # Java, built into the Platform (as shipped today)
    OpenIDE-Module-Source: java        # ditto, explicit
    OpenIDE-Module-Source: clojure     # a registered SourceRuntime with id "clojure" is required

The `SourceRuntime` SPI lives in `o.n.bootstrap` (same pattern as
`NetigsoFramework`: abstract base in the bootstrap, implementation in a
regular module):

```java
public abstract class SourceRuntime {
    public static final String TOKEN_PREFIX = "org.netbeans.modules.source.runtime.";
    /** Identity, e.g. "java", "clojure", "jruby". */
    public abstract String getId();
    /** Token the provider module must declare in OpenIDE-Module-Provides. */
    public final String getToken() { return TOKEN_PREFIX + getId(); }
    /** Version string included in the staleness stamp (e.g. compiler version). */
    public abstract String getVersion();
    /** Interpreter mode: serve classes/resources from source form; nothing
        is materialized or cached. Default: null (bytecode mode). */
    public ClassLoader createSourceLoader(Module m, List<File> classpath, ClassLoader[] parents) { return null; }
    /** Bytecode mode: build loadable output into the (empty) directory. */
    public void build(File srcDir, File outputDir, List<File> classpath) throws IOException { ... }
    /** Finds a runtime: "true"/"java" -> built-in; otherwise enabled token providers. */
    public static SourceRuntime find(ModuleManager mgr, String id) throws IOException;
}
```

The built-in Java runtime (`JavaSourceRuntime`) is what the Platform
supports out of the box - it needs nothing but the JDK. Runtimes for
other languages are regular modules: they declare
`OpenIDE-Module-Provides: org.netbeans.modules.source.runtime.<id>`,
register the implementation in
`META-INF/services/org.netbeans.SourceRuntime`, and are found through the
*enabled* module that provides the token. A module using such a runtime
must declare the matching `OpenIDE-Module-Requires` token (validated with
an instructive error otherwise), which makes the existing dependency
resolver enable the runtime before its source modules - the
chicken-and-egg solved with zero new ordering concepts.

Two integration modes exist; a runtime picks one:

* **Interpreter mode** (the purest form): `createSourceLoader` returns a
  loader that serves the module's classes and resources directly from
  source form - interpreting, or compiling in memory. Nothing is
  materialized, nothing is cached: the module JAR of plain source code is
  the entire deployment artifact. The module system wraps the returned
  loader with the full module classloader semantics
  (`RuntimeModuleClassLoader`: parent modules, package coverage, resource
  delegation, module identity), so implementors only handle their
  language. Bytecode is not required at all - it is merely an
  optimization available to runtimes that want caching.
* **Bytecode mode**: `build` produces class files and resources in an
  output directory, loaded by the ordinary module classloader. This buys
  a compile-once cache and staleness-based recompilation.

Everything else is language-agnostic and handled by the module system:
manifest and dependency semantics, staleness stamping (source digest +
classpath signatures + `getVersion()`), extraction (bytecode mode),
patches, failure reporting through module problems. A runtime can emit
`META-INF/generated-layer.xml` and `META-INF/services/*` during its build
or serve handwritten ones from the source JAR (interpreter mode), so a
Clojure or Groovy module can register actions and lookups just like a
Java one.

This is covered by unit tests with simulated languages
(`SourceRuntimeTest`): a bytecode-mode runtime registered through the
token/service mechanism (materialization, version-change cache
invalidation, misconfiguration errors) and an **interpreter-mode**
runtime whose loader serves classes from the source JAR by compiling them
in memory on demand - with handwritten service registrations read
straight from source form and no cache directory created at all.

Known limitation: interpreter-mode modules are visible to their
dependents at runtime (classloader parents, as usual) but not at *compile
time* - a bytecode runtime cannot compile against classes that only
exist in memory. Dependents of interpreted modules should themselves be
interpreted, or the runtime should materialize output.

## Notes for later

* **Demo runtime: Groovy - VALIDATED LIVE.** The SPI is complete enough
  to run real, non-Java source modules today; proven with Groovy 4.0.24
  and two hand-crafted module JARs booted in the full IDE:

  - a **runtime module** (`org.netbeans.libs.groovysrc`): ordinary
    module declaring
    `OpenIDE-Module-Provides: org.netbeans.modules.source.runtime.groovy`,
    shipping groovy-all.jar on its `Class-Path`, and registering a
    30-line `GroovySourceRuntime` via
    `META-INF/services/org.netbeans.SourceRuntime`:

  ```java
  public class GroovySourceRuntime extends SourceRuntime {
      public @Override String getId() { return "groovy"; }
      public @Override String getVersion() { return GroovySystem.getVersion(); }
      public @Override ClassLoader createSourceLoader(Module m, List<File> cp, ClassLoader[] parents) throws IOException {
          GroovyClassLoader gcl = new GroovyClassLoader(parents.length == 0 ? null : parents[0]);
          gcl.addClasspath(m.getJarFile().toURI().toURL().toString()); // the .groovy sources
          for (File f : cp) {
              gcl.addClasspath(f.toURI().toURL().toString()); // compile against deps
          }
          return gcl; // the module system wraps it with full module semantics
      }
  }
  ```

  - a **pure-Groovy source module** (`org.foo.groovydemo`): manifest with
    `OpenIDE-Module-Source: groovy`,
    `OpenIDE-Module-Requires: org.netbeans.modules.source.runtime.groovy`,
    a module dependency on the runtime module, and a
    `OpenIDE-Module-Install: org/foo/groovydemo/Installer.class`
    pointing at `Installer.groovy` (which the module system class-loads
    at enablement - through Groovy, in memory); the JAR holds only
    `.groovy` sources, a `Bundle.properties`, and a handwritten
    `META-INF/services` registration served straight from the JAR.

  Boot result: both modules enabled, the installer was compiled in
  memory by GroovyClassLoader on first class load, executed at startup,
  called `NbBundle` from a dependency module (parent-classloader
  semantics intact), and printed `GROOVY-DEMO ran!` - with **zero**
  javac/`javax.tools` involvement anywhere in the session. Interpreter
  mode, nothing materialized or cached.

  Remaining for production use: bytecode mode via `FileSystemCompiler`
  for cached startup (the stamp already includes `getVersion()`, so a
  Groovy upgrade invalidates caches); a real repo module carrying
  groovy-all.jar as an external binary; harness packaging rules for
  non-java source roots; and `OpenIDE-Module-Install` in Groovy source
  form (note: the header takes a `.class` resource path - a runtime
  resolving the installer class through its own loader needs the
  resource to exist, so interpreter-mode installers may need a small
  module-system accommodation).

* **Build systems and the SPI.** The built-in Java runtime bakes in the
  ant-harness conventions (sources at the JAR root, resource copying,
  processorpath = declared dependency JARs, no `-A` options) - which is
  correct, since that is what a "plain" NetBeans module *is*. A module
  that needs a dynamic build (e.g. Gradle with a build script) does not
  fit that runtime, but it does not break the design: a build system is
  just another `SourceRuntime` (the module JAR would ship its sources plus
  its build script, and a `gradle` runtime would run the build in
  `build()`). Known gaps to solve before that is real:
  1. the staleness stamp is computed by the driver from source-JAR
     contents, classpath files and the runtime version - a dynamic build
     has more inputs (build scripts, resolved dependency versions,
     toolchain versions), so the SPI will need a per-runtime
     staleness-input hook;
  2. the classpath handed to `build()` mirrors the *manifest-declared*
     dependency closure - a build system that resolves its own classpath
     dynamically bypasses the module system's closed-world package
     visibility, so what it compiles against may not exist at runtime;
  3. executing a build script at module enablement is arbitrary code
     execution at startup - the trust model (signed modules? dev-mode
     only?) and autoupdate reproducibility need thought.


## Pointers

* Runtime: `platform/o.n.bootstrap/src/org/netbeans/SourceModule.java`,
  `SourceRuntime.java` (SPI), `JavaSourceRuntime.java` (built-in),
  `SourceModuleCompiler.java` (language-agnostic driver),
  `ModuleFactory.java`, `StandardModule.java` (`findPatches` visibility).
* Tests: `platform/o.n.bootstrap/test/unit/src/org/netbeans/SourceModuleTest.java`
  (Java path), `SourceRuntimeTest.java` (SPI with a simulated
  non-Java runtime).
* Harness: `nbbuild/templates/common.xml` (`-do-jar-source-pure`,
  `-do-jar-source-hybrid`, `-check-compile`).
