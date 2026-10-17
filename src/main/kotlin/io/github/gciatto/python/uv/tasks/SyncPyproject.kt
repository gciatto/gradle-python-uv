package io.github.gciatto.python.uv.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Merges the Gradle-side `pyproject.toml` specification into `pyproject.toml` (`generatePyproject`),
 * or verifies that they are in sync (`checkPyproject`, if [checkOnly] is true).
 *
 * The merge is performed by a bundled Python script relying on `tomlkit`, which preserves
 * comments and formatting; uv provisions it in an ephemeral environment.
 */
@DisableCachingByDefault(because = "Edits a source file")
abstract class SyncPyproject : UvTask() {
    /** The JSON specification. */
    @get:Input
    abstract val specification: Property<String>

    /** The `pyproject.toml` file to generate or check. */
    @get:Internal
    abstract val pyprojectFile: RegularFileProperty

    /** Fingerprint of `pyproject.toml`, for up-to-date checks of `checkPyproject`. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pyprojectInput: RegularFileProperty

    /** Marker file written on success, for up-to-date checks of `checkPyproject`. */
    @get:OutputFile
    @get:Optional
    abstract val marker: RegularFileProperty

    /** Whether to only check for differences. */
    @get:Input
    abstract val checkOnly: Property<Boolean>

    /** Python packages required by the merge script. */
    @get:Input
    abstract val scriptRequirements: ListProperty<String>

    /** Where the merge script and the specification are written. */
    @get:Internal
    abstract val scriptDirectory: DirectoryProperty

    /** Runs the merge script. */
    @TaskAction
    fun synchronize() {
        val directory = scriptDirectory.get().asFile.apply { mkdirs() }
        val script = directory.resolve(SCRIPT_NAME)
        val scriptContent =
            checkNotNull(javaClass.getResourceAsStream(SCRIPT_RESOURCE)) { "Missing resource $SCRIPT_RESOURCE" }
                .use { it.readBytes() }
        script.writeBytes(scriptContent)
        val spec = directory.resolve("$name-spec.json").apply { writeText(specification.get()) }
        val pyproject = pyprojectFile.get().asFile
        val arguments =
            buildList {
                addAll(listOf("run", "--no-project", "--quiet"))
                scriptRequirements.get().forEach { addAll(listOf("--with", it)) }
                addAll(listOf("python", script.absolutePath, "--spec", spec.absolutePath))
                addAll(listOf("--pyproject", pyproject.absolutePath))
                if (checkOnly.get()) add("--check")
            }
        val result = runUv(arguments, ignoreExitValue = true)
        when (result.exitValue) {
            0 -> marker.orNull?.asFile?.writeText("${pyproject.name} in sync\n")

            1 -> throw GradleException(
                "$pyproject is out of sync with the Gradle configuration (see the diff above): " +
                    "run `./gradlew generatePyproject` and commit the result",
            )

            else -> throw GradleException("Failed to synchronize $pyproject (exit code ${result.exitValue})")
        }
    }

    private companion object {
        private const val SCRIPT_NAME = "pyproject_sync.py"
        private const val SCRIPT_RESOURCE = "/io/github/gciatto/python/uv/$SCRIPT_NAME"
    }
}
