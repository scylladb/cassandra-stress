package org.apache.cassandra.stress.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Writes a JVM options file that bin/cassandra-stress reads. A line holds one
 * option. A line "<min>-<max>:<option>" or "<min>-:<option>" applies only to
 * the Java versions in that range. A bound is a dotted version, and a short
 * bound covers its updates, so 21.0.9-21 holds 21.0.9 and every later 21.
 */
abstract class JvmOptions : DefaultTask() {
    @get:Input
    abstract val header: Property<String>

    @get:Input
    abstract val options: ListProperty<String>

    @get:OutputFile
    abstract val optionsFile: RegularFileProperty

    @TaskAction
    fun write() {
        val lines = options.get()
        lines.filterNot { LINE.matches(it) }.takeIf { it.isNotEmpty() }?.let {
            throw GradleException("These lines are not '[<min>-[<max>]:]<option>': ${it.joinToString()}")
        }
        val comments = header.get().lines().map { if (it.isEmpty()) "#" else "# $it" }
        optionsFile.get().asFile.writeText((comments + "" + lines).joinToString("\n", postfix = "\n"))
    }

    companion object {
        private val LINE = Regex("""(\d+(\.\d+)*-(\d+(\.\d+)*)?:)?-\S+""")
    }
}
