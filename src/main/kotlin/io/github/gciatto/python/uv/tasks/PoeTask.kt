package io.github.gciatto.python.uv.tasks

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/**
 * Runs a [Poe the Poet](https://poethepoet.natn.io/) task via `uv run poe <task>`.
 *
 * By default, it runs within the project environment (created by `uvSync`) without re-syncing it.
 * If [pythonVersion] is set, uv creates (or reuses) a dedicated environment for that version,
 * as configured by the `UV_PROJECT_ENVIRONMENT` variable in [environment].
 */
@DisableCachingByDefault(because = "Poe tasks may have arbitrary side effects")
abstract class PoeTask : UvTask() {
    /** The name of the Poe task. */
    @get:Input
    abstract val poeTask: Property<String>

    /** Arguments appended to the Poe task invocation. */
    @get:Input
    abstract val extraArguments: ListProperty<String>

    /** The Python version to run the task with, if different from the project's one. */
    @get:Input
    @get:Optional
    abstract val pythonVersion: Property<String>

    /** Sets [extraArguments] from the command line (whitespace-separated). */
    @Option(option = "args", description = "Arguments passed to the Poe task, separated by whitespace")
    fun setArgs(arguments: String) {
        extraArguments.addAll(arguments.split(Regex("\\s+")).filter { it.isNotEmpty() })
    }

    /** Runs the Poe task. */
    @TaskAction
    fun runPoe() {
        val arguments =
            buildList {
                add("run")
                pythonVersion.orNull?.let { addAll(listOf("--python", it)) } ?: add("--no-sync")
                add("poe")
                add(poeTask.get())
                addAll(extraArguments.get())
            }
        runUv(arguments)
    }
}
