package io.github.gciatto.python.uv

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Configuration of Python package publication (via `uv publish`).
 */
abstract class PythonPublishingSpec
@Inject
constructor(objects: ObjectFactory) {
    /**
     * Package indexes where distributions can be published.
     * `pypi` and `testpypi` are registered by default.
     * All indexes but `pypi` are written into `pyproject.toml` as `[[tool.uv.index]]` entries.
     */
    val repositories: NamedDomainObjectContainer<PythonRepository> =
        objects.domainObjectContainer(PythonRepository::class.java)

    /** The repository targeted by the `publish` lifecycle task, defaults to `pypi`. */
    abstract val defaultRepository: Property<String>

    /** Whether `uv publish` should be run with `--dry-run`. */
    abstract val dryRun: Property<Boolean>

    /** Registers or configures the repository named [name]. */
    fun repository(name: String, action: Action<in PythonRepository>): PythonRepository =
        repositories.maybeCreate(name).also(action::execute)
}

/**
 * A Python package index.
 */
abstract class PythonRepository
@Inject
constructor(private val name: String) : Named {
    /** The URL of the simple index (e.g. `https://test.pypi.org/simple/`). Unset for PyPI. */
    abstract val url: Property<String>

    /** The upload URL (e.g. `https://test.pypi.org/legacy/`). Unset for PyPI. */
    abstract val publishUrl: Property<String>

    /**
     * The token used for publishing.
     * Defaults to the `<NAME>_TOKEN` environment variable (e.g. `PYPI_TOKEN`, `TEST_PYPI_TOKEN`),
     * or to the `<name>Token` Gradle property (e.g. `pypiToken`).
     */
    abstract val token: Property<String>

    /** Whether this index is the default PyPI one (i.e. it has no explicit URL). */
    val isPyPI: Boolean
        get() = name == PYPI

    override fun getName(): String = name

    /** Well-known repository names. */

    companion object {
        /** The name of the default index. */
        const val PYPI = "pypi"

        /** The name of the test index. */
        const val TEST_PYPI = "testpypi"
    }
}
