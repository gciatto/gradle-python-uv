package io.github.gciatto.python.uv

import org.gradle.api.provider.ListProperty

/**
 * Maps Gradle's conventional lifecycle tasks onto Poe the Poet tasks.
 * Each list is executed in order.
 */
abstract class LifecycleMapping {
    /** Poe tasks run by `classes` (and hence `testClasses`), defaults to `compile`. */
    abstract val classes: ListProperty<String>

    /** Poe tasks run by `test`, defaults to `test`. */
    abstract val test: ListProperty<String>

    /** Poe tasks run by `check` (besides `test`), defaults to `static-checks`. */
    abstract val check: ListProperty<String>

    /** Poe tasks run by `format`, defaults to `static-fixes`. */
    abstract val format: ListProperty<String>

    /** Poe tasks run by `coverage`, defaults to `coverage`, `coverage-report`. */
    abstract val coverage: ListProperty<String>

    /** All the Poe tasks referenced by this mapping. */
    internal val allPoeTasks: List<String>
        get() = listOf(classes, test, check, format, coverage).flatMap { it.get() }
}
