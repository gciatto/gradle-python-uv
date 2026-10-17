package io.github.gciatto.python.uv.tasks

import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ProcessExecutionException
import org.gradle.work.DisableCachingByDefault

/**
 * Base class for tasks invoking uv.
 */
@DisableCachingByDefault(because = "Runs an external process")
abstract class UvTask : DefaultTask() {
    /** The uv executable. */
    @get:Input
    abstract val uvExecutable: Property<String>

    /** The directory uv is run from (i.e. the Python project directory). */
    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    /** Additional environment variables for uv. */
    @get:Input
    abstract val environment: MapProperty<String, String>

    /** Process launcher. */
    @get:Inject
    protected abstract val execOperations: ExecOperations

    /**
     * Runs uv with the given [arguments].
     * If [ignoreExitValue] is true, the caller is responsible for checking the result.
     */
    protected fun runUv(
        arguments: List<String>,
        directory: File = workingDirectory.get().asFile,
        extraEnvironment: Map<String, String> = emptyMap(),
        ignoreExitValue: Boolean = false,
    ): ExecResult {
        val command = listOf(uvExecutable.get()) + arguments
        logger.info("Running {} in {}", command, directory)
        return try {
            execOperations.exec { spec ->
                spec.commandLine(command)
                spec.workingDir(directory)
                spec.environment(environment.get() + extraEnvironment)
                spec.isIgnoreExitValue = ignoreExitValue
            }
        } catch (e: ProcessExecutionException) {
            throw GradleException(failureMessage(command, e), e)
        }
    }

    private fun failureMessage(command: List<String>, e: Exception): String = if (isUvMissing()) {
        "Cannot run '${uvExecutable.get()}': is uv installed? " +
            "See https://docs.astral.sh/uv/getting-started/installation/, " +
            "or set python.uvExecutable to its absolute path."
    } else {
        "Command failed: ${command.joinToString(" ")}\n${e.message}"
    }

    private fun isUvMissing(): Boolean = runCatching {
        execOperations.exec { spec ->
            spec.commandLine(uvExecutable.get(), "--version")
            spec.standardOutput = ByteArrayOutputStream()
            spec.isIgnoreExitValue = true
        }
    }.isFailure
}

/**
 * Runs uv with arbitrary [arguments] (e.g. `uv lock`, `uv python install 3.12`).
 */
@DisableCachingByDefault(because = "Runs an external process")
abstract class UvExec : UvTask() {
    /** The arguments passed to uv. */
    @get:Input
    abstract val arguments: ListProperty<String>

    /** Runs the command. */
    @TaskAction
    fun execute() {
        runUv(arguments.get())
    }
}
