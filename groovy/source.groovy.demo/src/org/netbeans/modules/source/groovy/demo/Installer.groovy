package org.netbeans.modules.source.groovy.demo

import org.openide.modules.ModuleInstall

/**
 * This module ships as pure Groovy source code: its JAR contains
 * .groovy sources where other modules contain compiled classes. The
 * module system hands it to the Groovy source runtime, which defines
 * the classes in memory on first use - nothing is ever written to disk.
 */
class Installer extends ModuleInstall {

    @Override
    void restored() {
        def msg = org.openide.util.NbBundle.getMessage(Installer.class, "MSG_Welcome")
        System.err.println("GROOVY-SOURCE-DEMO " + msg
            + " (Groovy " + groovy.lang.GroovySystem.getVersion()
            + ", interpreted by " + Installer.class.getClassLoader() + ")")
    }
}
