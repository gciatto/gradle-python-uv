package io.github.gciatto.python.uv.tasks

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs the Python application, i.e. `uv run <script> [args]`, or `uv run python -m <module> [args]`
 * if no [script] is configured.
 */
@DisableCachingByDefault(because = "Runs an application")
abstract class UvRun : UvTask() {
    /** The console script to run. */
    @get:Input
    @get:Optional
    abstract val script: Property<String>

    /** The module to run when [script] is absent. */
    @get:Input
    abstract val module: Property<String>

    /** Application arguments. */
    @get:Input
    abstract val applicationArguments: ListProperty<String>

    /** Sets [applicationArguments] from the command line (whitespace-separated), like `run --args`. */
    @Option(option = "args", description = "Command line arguments passed to the application")
    fun setArgs(arguments: String) {
        applicationArguments.addAll(arguments.split(Regex("\\s+")).filter { it.isNotEmpty() })
    }

    /** Runs the application. */
    @TaskAction
    fun runApplication() {
        val target = script.orNull?.let { listOf(it) } ?: listOf("python", "-m", module.get())
        runUv(listOf("run", "--no-sync") + target + applicationArguments.get())
    }
}
