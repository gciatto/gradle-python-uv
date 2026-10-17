package io.github.gciatto.python.uv

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * The `python { ... }` extension.
 */
abstract class PythonExtension
@Inject
constructor(objects: ObjectFactory) {
    /**
     * The uv executable: either a path, or a command name looked up in `PATH` and in well-known locations.
     * Defaults to the `python.uvExecutable` Gradle property, or to the `UV_EXECUTABLE` environment variable.
     * If unset, uv is looked up or downloaded according to [uvProvisioning].
     */
    abstract val uvExecutable: Property<String>

    /** How uv is obtained, defaults to the `python.uvProvisioning` Gradle property, or to [UvProvisioning.AUTO]. */
    abstract val uvProvisioning: Property<UvProvisioning>

    /**
     * The uv version downloaded when needed,
     * defaults to the `python.uvVersion` Gradle property, or to a pinned version.
     */
    abstract val uvVersion: Property<String>

    /**
     * The (PEP 503 simple) package index uv is downloaded from, defaults to the `python.uvIndexUrl` Gradle property,
     * to the `UV_DEFAULT_INDEX`, `UV_INDEX_URL`, or `PIP_INDEX_URL` environment variables, or to PyPI.
     */
    abstract val uvIndexUrl: Property<String>

    /** Where downloaded uv versions are stored, defaults to `<Gradle user home>/caches/python-uv/uv`. */
    abstract val uvCacheDirectory: DirectoryProperty

    /** The Python version for the project environment, defaults to the content of `.python-version`. */
    abstract val pythonVersion: Property<String>

    /** Python versions to run tests against via `testAllPythonVersions` (one virtual environment each). */
    abstract val testedPythonVersions: ListProperty<String>

    /** The `pyproject.toml` file. */
    abstract val pyprojectFile: RegularFileProperty

    /** The PEP 440 version of the package, defaults to the conversion of `project.version`. */
    abstract val version: Property<String>

    /**
     * Whether [version] should be written into `pyproject.toml` by `generatePyproject`.
     * Defaults to false: the version is injected when building distributions, so that the
     * tracked `pyproject.toml` does not change at every commit.
     */
    abstract val writeVersion: Property<Boolean>

    /** Whether `uv sync` should be run with `--locked`, defaults to true on CI (i.e. if `CI=true`). */
    abstract val locked: Property<Boolean>

    /** Whether `clean` should delete the `.venv` directory as well, defaults to false. */
    abstract val cleanVirtualEnv: Property<Boolean>

    /**
     * The console script executed by `run`, defaults to the first one in `pyproject.toml`.
     * If no script is available, `run` executes `python -m <moduleName>`.
     */
    abstract val runScript: Property<String>

    /** The `pyproject.toml` specification. */
    val pyproject: PyprojectSpec = objects.newInstance(PyprojectSpec::class.java)

    /** The mapping of Gradle lifecycle tasks onto Poe tasks. */
    val lifecycle: LifecycleMapping = objects.newInstance(LifecycleMapping::class.java)

    /** Publishing configuration. */
    val publishing: PythonPublishingSpec = objects.newInstance(PythonPublishingSpec::class.java)

    /** Configures [pyproject]. */
    fun pyproject(action: Action<in PyprojectSpec>) = action.execute(pyproject)

    /** Configures [lifecycle]. */
    fun lifecycle(action: Action<in LifecycleMapping>) = action.execute(lifecycle)

    /** Configures [publishing]. */
    fun publishing(action: Action<in PythonPublishingSpec>) = action.execute(publishing)

    /** Sets the Python versions to test against. */
    fun testedPythonVersions(vararg versions: String) = testedPythonVersions.set(versions.toList())

    /** Constants related to the extension. */

    companion object {
        /** The name of this extension. */
        const val NAME = "python"
    }
}
