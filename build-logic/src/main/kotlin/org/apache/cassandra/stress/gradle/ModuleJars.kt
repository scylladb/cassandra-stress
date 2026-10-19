package org.apache.cassandra.stress.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.registerTransform
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * The module names for the dependency jars that have no module descriptor and
 * no Automatic-Module-Name, keyed by the jar name without the version.
 */
val AUTOMATIC_MODULE_NAMES = mapOf(
    "scylla-driver-core" to "com.scylladb.driver.core",
    "java-driver-guava-shaded" to "com.datastax.oss.driver.shaded.guava",
    "commons-math3" to "org.apache.commons.math3",
    "HdrHistogram" to "org.hdrhistogram.HdrHistogram",
)

/**
 * Service files to remove, keyed like [AUTOMATIC_MODULE_NAMES]. The shaded
 * driver 3.x jar declares a BlockHound provider that the shading did not
 * relocate, and the module system refuses a jar with a missing provider.
 */
val REMOVED_SERVICES = mapOf(
    "scylla-driver-core" to listOf("META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"),
)

val MODULE_JAR: Attribute<Boolean> = Attribute.of("org.apache.cassandra.stress.module-jar", Boolean::class.javaObjectType)

/**
 * Gives every dependency jar a module name, so Gradle puts it on the module
 * path, and removes the service files that the module system refuses.
 */
abstract class ModuleJarTransform : TransformAction<ModuleJarTransform.Parameters> {
    interface Parameters : TransformParameters {
        @get:Input
        val automaticModuleNames: MapProperty<String, String>

        @get:Input
        val removedServices: MapProperty<String, List<String>>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jar = inputArtifact.get().asFile
        val key = parameters.automaticModuleNames.get().keys.firstOrNull { jar.name.startsWith("$it-") }
        if (key == null) {
            outputs.file(inputArtifact)
            return
        }
        val removed = parameters.removedServices.get()[key].orEmpty().toSet()
        JarFile(jar).use { input ->
            val manifest = input.manifest ?: Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            }
            manifest.mainAttributes.putValue("Automatic-Module-Name", parameters.automaticModuleNames.get().getValue(key))
            JarOutputStream(outputs.file(jar.name).outputStream(), manifest).use { output ->
                for (entry in input.entries()) {
                    if (entry.name.equals(JarFile.MANIFEST_NAME, ignoreCase = true) || entry.name in removed) {
                        continue
                    }
                    output.putNextEntry(JarEntry(entry.name))
                    input.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
    }
}

/** Resolves every classpath of the project through [ModuleJarTransform]. */
fun Project.useModuleJars() {
    dependencies.attributesSchema.attribute(MODULE_JAR)
    dependencies.artifactTypes.maybeCreate(ArtifactTypeDefinition.JAR_TYPE).attributes.attribute(MODULE_JAR, false)
    dependencies.registerTransform(ModuleJarTransform::class) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
            .attribute(MODULE_JAR, false)
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
            .attribute(MODULE_JAR, true)
        parameters {
            automaticModuleNames.putAll(AUTOMATIC_MODULE_NAMES)
            removedServices.putAll(REMOVED_SERVICES)
        }
    }
    configurations.configureEach {
        if (isCanBeResolved && !isCanBeConsumed) {
            attributes.attribute(MODULE_JAR, true)
        }
    }
}
