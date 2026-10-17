package io.github.gciatto.python.uv

import org.tomlj.Toml
import org.tomlj.TomlParseResult

/**
 * Reads the information Gradle needs at configuration time from the content of `pyproject.toml`.
 */
internal class PyprojectReader(content: String?) {
    private val toml: TomlParseResult? = content?.let(Toml::parse)?.takeUnless { it.hasErrors() }

    /** The names of the tasks in `[tool.poe.tasks]`. */
    val poeTasks: List<String>
        get() = keysOf("tool", "poe", "tasks")

    /** The names of the scripts in `[project.scripts]`. */
    val scripts: List<String>
        get() = keysOf("project", "scripts")

    private fun keysOf(vararg path: String): List<String> = toml
        ?.getTable(path.toList())
        ?.keySet()
        ?.toList()
        .orEmpty()
}
