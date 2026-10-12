package org.apache.cassandra.stress.gradle

import java.io.File
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Writes a SHA-256 and a SHA-512 file next to each archive. Each file holds the hex digest only. */
abstract class Checksums : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val archives: ConfigurableFileCollection

    @get:OutputFiles
    val checksumFiles: Provider<List<File>>
        get() = archives.elements.map { files ->
            files.flatMap { file -> ALGORITHMS.keys.map { ext -> File("${file.asFile.path}.$ext") } }
        }

    @TaskAction
    fun write() {
        // A skipped archive task, such as sourceTar outside a git checkout, writes no archive.
        archives.filter { it.isFile }.forEach { archive ->
            val digests = ALGORITHMS.mapValues { (_, algorithm) -> MessageDigest.getInstance(algorithm) }
            archive.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    digests.values.forEach { it.update(buffer, 0, read) }
                }
            }
            digests.forEach { (ext, digest) ->
                File("${archive.path}.$ext").writeText(digest.digest().joinToString("") { "%02x".format(it) })
            }
        }
    }

    companion object {
        val ALGORITHMS = mapOf("sha256" to "SHA-256", "sha512" to "SHA-512")
    }
}
