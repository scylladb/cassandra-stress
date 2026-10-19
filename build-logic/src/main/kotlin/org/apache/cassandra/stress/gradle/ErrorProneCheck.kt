package org.apache.cassandra.stress.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Compiles the main sources with Error Prone in a separate javac process and
 * fails on every finding, at warning level too. A finding has the form
 * "warning: [CheckName]". The JDK also warns on each use of sun.misc.Signal,
 * which has no public replacement and no way to suppress the warning, so
 * javac -Werror cannot do this check.
 */
abstract class ErrorProneCheck @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:Nested
    abstract val compiler: Property<JavaCompiler>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Classpath
    abstract val errorProneClasspath: ConfigurableFileCollection

    @get:Input
    abstract val release: Property<Int>

    @get:Input
    abstract val disabledChecks: ListProperty<String>

    @get:OutputDirectory
    abstract val classesDirectory: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val classes = classesDirectory.get().asFile
        classes.deleteRecursively()
        classes.mkdirs()
        val sourceFiles = sources.files
        val sourceList = temporaryDir.resolve("sources.txt")
        sourceList.writeText(sourceFiles.joinToString("\n") { "\"" + it.path.replace("\\", "\\\\") + "\"" })
        val pathOption = if (sourceFiles.any { it.name == "module-info.java" }) "--module-path" else "-cp"

        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            executable = compiler.get().executablePath.asFile.path
            args(JDK_COMPILER_PACKAGES.map { "-J--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" })
            args(OPENED_PACKAGES.map { "-J--add-opens=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" })
            args("-XDcompilePolicy=simple", "--should-stop=ifError=FLOW", "-XDaddTypeAnnotationsToSymbol=true")
            args("--release", release.get(), "-encoding", "UTF-8", "-proc:none", "-Xmaxwarns", "10000")
            args("-processorpath", errorProneClasspath.asPath)
            args(
                (
                    listOf("-Xplugin:ErrorProne", "-XepDisableWarningsInGeneratedCode") +
                        disabledChecks.get().map { "-Xep:$it:OFF" }
                    ).joinToString(" "),
            )
            args(pathOption, classpath.asPath, "-d", classes.path, "@${sourceList.path}")
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }

        val text = output.toString(Charsets.UTF_8)
        report.get().asFile.writeText(text)
        if (text.isNotBlank()) {
            logger.lifecycle(text)
        }
        if (result.exitValue != 0 || FINDING.containsMatchIn(text)) {
            throw GradleException("Error Prone reported findings. See ${report.get().asFile}.")
        }
    }

    companion object {
        val JDK_COMPILER_PACKAGES = listOf(
            "api", "code", "comp", "file", "main", "model", "parser", "processing", "tree", "util",
        )
        val OPENED_PACKAGES = listOf("code", "comp")
        val FINDING = Regex("(warning|error): \\[[A-Za-z]+\\]")
    }
}
