package io.github.gciatto.python.uv.tasks

import io.github.gciatto.python.uv.UvProvisioning
import io.github.gciatto.python.uv.provisioning.UvResolver
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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ProcessExecutionException
import org.gradle.work.DisableCachingByDefault

/**
 * Base class for tasks invoking uv.
 * The uv executable is resolved at execution time (see [UvResolver]), and downloaded if needed.
 */
@DisableCachingByDefault(because = "Runs an external process")
abstract class UvTask : DefaultTask() {
    /** The configured uv executable (path or command name), if any. */
    @get:Input
    @get:Optional
    abstract val uvExecutable: Property<String>

    /** How uv is obtained when [uvExecutable] is not set. */
    @get:Input
    abstract val uvProvisioning: Property<UvProvisioning>

    /** The uv version to download, if needed. */
    @get:Input
    abstract val uvVersion: Property<String>

    /** The (simple) package index uv is downloaded from, if needed. */
    @get:Input
    abstract val uvIndexUrl: Property<String>

    /** Where downloaded uv versions are stored. */
    @get:Internal
    abstract val uvCacheDirectory: DirectoryProperty

    /** The directory uv is run from (i.e. the Python project directory). */
    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    /** Additional environment variables for uv. */
    @get:Input
    abstract val environment: MapProperty<String, String>

    /** Process launcher. */
    @get:Inject
    protected abstract val execOperations: ExecOperations

    /** Resolves the uv executable, downloading it if needed. */
    protected fun resolveUv(): File = UvResolver.resolve(
        UvResolver.Request(
            explicitExecutable = uvExecutable.orNull,
            provisioning = uvProvisioning.get(),
            version = uvVersion.get(),
            indexUrl = uvIndexUrl.get(),
            cacheDirectory = uvCacheDirectory.get().asFile,
            baseDirectory = workingDirectory.get().asFile,
        ),
        logger,
    )

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
        val command = listOf(resolveUv().absolutePath) + arguments
        logger.info("Running {} in {}", command, directory)
        return try {
            execOperations.exec { spec ->
                spec.commandLine(command)
                spec.workingDir(directory)
                spec.environment(environment.get() + extraEnvironment)
                spec.isIgnoreExitValue = ignoreExitValue
            }
        } catch (e: ProcessExecutionException) {
            throw GradleException("Command failed: ${command.joinToString(" ")}\n${e.message}", e)
        }
    }
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

/**
 * Makes uv available (downloading it, if needed and allowed), and prints its location and version.
 */
@DisableCachingByDefault(because = "uv is installed into the Gradle user home")
abstract class UvInstall : UvTask() {
    /** Resolves uv and prints its version. */
    @TaskAction
    fun install() {
        logger.quiet("uv executable: {}", resolveUv())
        runUv(listOf("--version"))
    }
}
