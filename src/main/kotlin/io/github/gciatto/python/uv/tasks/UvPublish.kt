package io.github.gciatto.python.uv.tasks

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Publishes distributions via `uv publish`.
 * The token is passed through the `UV_PUBLISH_TOKEN` environment variable, so it never appears in logs.
 */
@DisableCachingByDefault(because = "Publishing is a side effect")
abstract class UvPublish : UvTask() {
    /** The distributions to publish. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val distributions: ConfigurableFileCollection

    /** The `[[tool.uv.index]]` name to publish to, absent for PyPI. */
    @get:Input
    @get:Optional
    abstract val index: Property<String>

    /** The publishing token, if any (otherwise, uv falls back to trusted publishing or other credentials). */
    @get:Internal
    abstract val token: Property<String>

    /** Whether to pass `--dry-run`. */
    @get:Input
    abstract val dryRun: Property<Boolean>

    /** Publishes the distributions. */
    @TaskAction
    fun publish() {
        val files = distributions.files.filter { it.isFile }
        if (files.isEmpty()) throw GradleException("No distributions to publish in ${distributions.asPath}")
        val arguments =
            buildList {
                add("publish")
                index.orNull?.let { addAll(listOf("--index", it)) }
                if (dryRun.get()) add("--dry-run")
                addAll(files.map { it.absolutePath })
            }
        val tokenEnvironment = token.orNull?.let { mapOf("UV_PUBLISH_TOKEN" to it) }.orEmpty()
        runUv(arguments, extraEnvironment = tokenEnvironment)
    }
}
