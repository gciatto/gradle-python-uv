package io.github.gciatto.python.uv

import java.io.Serializable
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * The Gradle-side description of `pyproject.toml`.
 *
 * Its content is merged into `pyproject.toml` by the `generatePyproject` task,
 * and compared against it by the `checkPyproject` task.
 * Defaults reproduce the conventions of
 * [template-python-project-uv](https://github.com/gciatto/template-python-project-uv).
 */
@Suppress("TooManyFunctions")
abstract class PyprojectSpec {
    /** `project.name`, defaults to the Gradle project name. */
    abstract val name: Property<String>

    /** `project.description`, defaults to the Gradle project description. */
    abstract val description: Property<String>

    /** `project.readme`, defaults to `README.md` (when it exists). */
    abstract val readme: Property<String>

    /** `project.license`, as an SPDX expression (PEP 639). */
    abstract val license: Property<String>

    /** `project.requires-python`, defaults to `>=3.10`. */
    abstract val requiresPython: Property<String>

    /** `project.keywords`. */
    abstract val keywords: ListProperty<String>

    /** `project.classifiers`. */
    abstract val classifiers: ListProperty<String>

    /** `project.authors`. */
    abstract val authors: ListProperty<Person>

    /** `project.urls`. */
    abstract val urls: MapProperty<String, String>

    /** `project.dependencies`, i.e. the runtime dependencies. */
    abstract val dependencies: ListProperty<String>

    /** `project.optional-dependencies`, i.e. the extras. */
    abstract val optionalDependencies: MapProperty<String, List<String>>

    /** `dependency-groups` (PEP 735), defaults to a `dev` group with coverage, mypy, pytest, ruff, poethepoet. */
    abstract val dependencyGroups: MapProperty<String, List<String>>

    /** `project.scripts`, defaults to `<moduleName> = "<moduleName>.__main__:main"` if `__main__.py` exists. */
    abstract val scripts: MapProperty<String, String>

    /** `build-system.requires`, defaults to the `uv_build` backend. */
    abstract val buildSystemRequires: ListProperty<String>

    /** `build-system.build-backend`, defaults to `uv_build`. */
    abstract val buildBackend: Property<String>

    /** `tool.uv.build-backend.module-name`, defaults to the [name] with dashes replaced by underscores. */
    abstract val moduleName: Property<String>

    /** `tool.uv.build-backend.module-root`, defaults to the empty string (i.e. flat layout). */
    abstract val moduleRoot: Property<String>

    /**
     * `tool.poe.tasks`: values are either strings (commands), lists of strings (sequences),
     * or maps (full Poe the Poet task definitions).
     */
    abstract val poeTasks: MapProperty<String, Any>

    /**
     * Other `[tool.*]` tables, indexed by dotted path relative to `tool`
     * (e.g. `ruff`, or `pytest.ini_options`).
     */
    abstract val tools: MapProperty<String, Map<String, Any>>

    /** Adds an author. */
    fun author(name: String, email: String? = null) {
        authors.add(Person(name, email))
    }

    /** Adds runtime dependencies (PEP 508 strings, e.g. `numpy>=2`). */
    fun dependency(vararg requirements: String) {
        dependencies.addAll(*requirements)
    }

    /** Adds dependencies to the extra named [extra]. */
    fun optionalDependency(extra: String, vararg requirements: String) {
        optionalDependencies.appendTo(extra, requirements.toList())
    }

    /** Adds dependencies to the group named [group]. */
    fun dependencyGroup(group: String, vararg requirements: String) {
        dependencyGroups.appendTo(group, requirements.toList())
    }

    /** Adds dependencies to the `dev` group. */
    fun devDependency(vararg requirements: String) {
        dependencyGroup(DEV_GROUP, *requirements)
    }

    /** Declares a console script. */
    fun script(name: String, entryPoint: String) {
        scripts.put(name, entryPoint)
    }

    /** Declares a Poe the Poet command task. */
    fun poeTask(name: String, command: String) {
        poeTasks.put(name, command)
    }

    /** Declares a Poe the Poet task by means of its full definition (e.g. `mapOf("shell" to "...")`). */
    fun poeTask(name: String, definition: Map<String, Any>) {
        poeTasks.put(name, definition)
    }

    /** Declares a Poe the Poet sequence task. */
    fun poeSequence(name: String, vararg tasks: String) {
        poeTasks.put(name, tasks.toList())
    }

    /** Sets (or replaces) the `[tool.<path>]` table. */
    fun tool(path: String, settings: Map<String, Any>) {
        tools.put(path, settings)
    }

    private fun MapProperty<String, List<String>>.appendTo(key: String, values: List<String>) {
        val current = getting(key).orNull.orEmpty()
        put(key, current + values)
    }

    /**
     * An author or maintainer of the Python project.
     *
     * @property name the full name
     * @property email the e-mail address, if any
     */
    data class Person(val name: String, val email: String? = null) : Serializable {
        /** The representation of this person in `pyproject.toml`. */
        fun toMap(): Map<String, String> = listOfNotNull("name" to name, email?.let { "email" to it }).toMap()

        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    /** Constants related to `pyproject.toml`. */

    companion object {
        /** The name of the default development dependency group. */
        const val DEV_GROUP = "dev"
    }
}
