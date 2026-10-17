package io.github.gciatto.python.uv.tasks

import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Builds the wheel and the source distribution via `uv build`.
 *
 * Sources are copied into [stagingDirectory] first, where the [version] is injected via `uv version`:
 * this way, the tracked `pyproject.toml` is never modified by builds.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class UvBuild : UvTask() {
    /** The files needed to build the distributions (pyproject.toml, README, the package, ...). */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The PEP 440 version of the distributions. */
    @get:Input
    abstract val version: Property<String>

    /** Where sources are staged. */
    @get:Internal
    abstract val stagingDirectory: DirectoryProperty

    /** Where distributions are written. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** File operations. */
    @get:Inject
    protected abstract val files: FileSystemOperations

    /** Builds the distributions. */
    @TaskAction
    fun build() {
        val staging = stagingDirectory.get().asFile
        val output = outputDirectory.get().asFile
        files.sync {
            it.from(sources)
            it.into(staging)
        }
        files.delete { it.delete(output.listFiles().orEmpty()) }
        runUv(listOf("version", version.get(), "--frozen"), directory = staging)
        runUv(listOf("build", "--out-dir", output.absolutePath), directory = staging)
    }
}
