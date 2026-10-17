package io.github.gciatto.python.uv.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
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
 * Runs `uv sync`, i.e. creates or updates the project's virtual environment.
 * The task is up-to-date as long as `pyproject.toml`, `uv.lock`, and `.python-version` do not change,
 * and the virtual environment still exists.
 */
@DisableCachingByDefault(because = "The virtual environment is machine-specific")
abstract class UvSync : UvTask() {
    /** The `pyproject.toml` file. */
    @get:Internal("Validated at execution time, to provide a meaningful error message")
    abstract val pyprojectFile: RegularFileProperty

    /** Fingerprint of `pyproject.toml` (optional, so that a missing file is reported by [sync]). */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pyprojectInput: RegularFileProperty

    /** The `uv.lock` file, if any. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: RegularFileProperty

    /** The `.python-version` file, if any. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pythonVersionFile: RegularFileProperty

    /** The Python version to use, if any. */
    @get:Input
    @get:Optional
    abstract val pythonVersion: Property<String>

    /** Whether to pass `--locked`. */
    @get:Input
    abstract val locked: Property<Boolean>

    /** Marker file, written into the virtual environment on success. */
    @get:OutputFile
    abstract val marker: RegularFileProperty

    /** Runs `uv sync`. */
    @TaskAction
    fun sync() {
        if (!pyprojectFile.get().asFile.exists()) {
            throw GradleException(
                "${pyprojectFile.get().asFile} does not exist: run `./gradlew generatePyproject` to create it",
            )
        }
        val arguments =
            buildList {
                add("sync")
                if (locked.get()) add("--locked")
                pythonVersion.orNull?.let { addAll(listOf("--python", it)) }
            }
        runUv(arguments)
        marker.get().asFile.writeText("synced by Gradle\n")
    }
}
