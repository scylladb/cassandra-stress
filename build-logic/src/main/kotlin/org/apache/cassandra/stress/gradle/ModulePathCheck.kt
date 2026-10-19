package org.apache.cassandra.stress.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.jar.JarFile

/**
 * Fails when a jar of the launcher module path has no module descriptor and no
 * Automatic-Module-Name. The JVM would name such a jar after its file name, and
 * the name would change with the version. Add the jar to AUTOMATIC_MODULE_NAMES.
 */
abstract class ModulePathCheck : DefaultTask() {
    @get:Classpath
    abstract val modulePath: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val unnamed = modulePath.files.filter { it.name.endsWith(".jar") }.filterNot(::isNamedModule).map { it.name }
        report.get().asFile.writeText(unnamed.joinToString("\n"))
        if (unnamed.isNotEmpty()) {
            throw GradleException(
                "These jars of the module path have no module name: ${unnamed.joinToString()}. " +
                    "Add each one to AUTOMATIC_MODULE_NAMES in ModuleJars.kt.",
            )
        }
    }

    private fun isNamedModule(jar: java.io.File): Boolean = JarFile(jar).use { input ->
        input.getEntry("module-info.class") != null ||
            input.manifest?.mainAttributes?.getValue("Automatic-Module-Name") != null ||
            input.entries().asSequence().any { it.name.matches(VERSIONED_MODULE_INFO) }
    }

    companion object {
        val VERSIONED_MODULE_INFO = Regex("META-INF/versions/\\d+/module-info\\.class")
    }
}
