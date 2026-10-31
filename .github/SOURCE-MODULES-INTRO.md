These branches let Apache NetBeans ship and load **modules as source
code**: a module JAR can carry `.java` (or `.groovy`) files in place of
compiled classes, and the running IDE compiles or interprets them on
demand - lazily, at first use, caching the result and recompiling
automatically whenever sources or dependencies change, so a patched
module is picked up without any rebuild.

The support is layered into small, individually reviewable branches:
`source-modules-engine` puts the machinery (extraction, staleness
detection, lazy classloading) plus a language-agnostic `SourceRuntime`
SPI into `o.n.bootstrap`; `source-modules-java-runtime` and
`source-modules-groovy-runtime` add the language runtimes as ordinary
autoload modules discovered through NetBeans' standard token mechanism -
the Groovy one is about thirty lines and never touches disk - which also
means externally built modules can be source-only with no harness
involvement; `source-modules-harness` teaches the ant build to package
such modules (hybrid, with classes for not-yet-converted dependents, or
pure); and `source-modules-conversions` flips 144 Platform and Java
cluster modules to the new form.

This build is a demo NetBeans in which well over a hundred of its own
modules ship as pure source JARs (compiled classes are stripped after
the build), compiled by the IDE itself on first launch - 127 on-demand
compiles with zero failures, and zero recompiles on the next start. Run
it on a JDK (the in-process `javax.tools` compiler needs one), and try
`unzip -l` on any of the JARs listed in the build log.
