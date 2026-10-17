package io.github.gciatto.python.uv

import java.util.Locale

/**
 * Builds the specification consumed by `pyproject_sync.py`:
 * - `document` contains the values managed by Gradle;
 * - `fallbacks` contains values written only if missing from `pyproject.toml`.
 */
internal object PyprojectSpecification {
    private const val UV_BUILD = "uv_build"

    fun of(extension: PythonExtension): Map<String, Any> {
        val spec = extension.pyproject
        val project =
            linkedMapOf<String, Any?>(
                "name" to spec.name.orNull,
                "version" to extension.version.orNull?.takeIf { extension.writeVersion.get() },
                "description" to spec.description.orNull,
                "readme" to spec.readme.orNull,
                "license" to spec.license.orNull,
                "authors" to spec.authors.get().map { it.toMap() },
                "keywords" to spec.keywords.get(),
                "classifiers" to spec.classifiers.get(),
                "requires-python" to spec.requiresPython.orNull,
                "dependencies" to spec.dependencies.get(),
                "optional-dependencies" to spec.optionalDependencies.get(),
                "scripts" to spec.scripts.get(),
                "urls" to spec.urls.get(),
            ).pruned(keep = setOf("dependencies"))
        val buildSystem =
            linkedMapOf<String, Any?>(
                "requires" to spec.buildSystemRequires.get(),
                "build-backend" to spec.buildBackend.orNull,
            ).pruned()
        val uv =
            linkedMapOf<String, Any?>(
                "build-backend" to
                    linkedMapOf<String, Any?>(
                        "module-name" to spec.moduleName.orNull,
                        "module-root" to spec.moduleRoot.orNull,
                    ).pruned().takeIf { spec.buildBackend.orNull == UV_BUILD },
                "index" to
                    extension.publishing.repositories
                        .filter { !it.isPyPI && it.url.isPresent }
                        .sortedBy { it.name }
                        .map { repository ->
                            linkedMapOf<String, Any?>(
                                "name" to repository.name,
                                "url" to repository.url.get(),
                                "publish-url" to repository.publishUrl.orNull,
                                "explicit" to true,
                            ).pruned()
                        },
            ).pruned()
        val tool = linkedMapOf<String, Any?>("uv" to uv, "poe" to mapOf("tasks" to spec.poeTasks.get()).pruned())
        spec.tools.get().forEach { (path, settings) -> tool.putAtPath(path.split('.'), settings) }
        val document =
            linkedMapOf<String, Any?>(
                "project" to project,
                "build-system" to buildSystem,
                "dependency-groups" to spec.dependencyGroups.get(),
                "tool" to tool.pruned(),
            ).pruned()
        val fallbacks = mapOf("project" to mapOf("version" to (extension.version.orNull ?: "0.0.0")))
        return mapOf("document" to document, "fallbacks" to fallbacks)
    }

    /** Removes null and empty values, as they are not managed by Gradle. */
    private fun Map<String, Any?>.pruned(keep: Set<String> = emptySet()): Map<String, Any> = buildMap {
        for ((key, value) in this@pruned) {
            val isEmpty =
                value == null || (value is Collection<*> && value.isEmpty()) ||
                    (value is Map<*, *> && value.isEmpty())
            if (!isEmpty || key in keep) put(key, value ?: emptyList<Any>())
        }
    }

    /** Puts [value] at the dotted [path], deep-merging tables (without mutating the original maps). */
    @Suppress("UNCHECKED_CAST")
    private fun MutableMap<String, Any?>.putAtPath(path: List<String>, value: Any) {
        val key = path.first()
        val existing = get(key)
        val merged: Any =
            when {
                path.size > 1 -> {
                    val child = LinkedHashMap((existing as? Map<String, Any?>).orEmpty())
                    child.putAtPath(path.drop(1), value)
                    child
                }

                existing is Map<*, *> && value is Map<*, *> -> {
                    val child = LinkedHashMap(existing as Map<String, Any?>)
                    (value as Map<String, Any>).forEach { (k, v) -> child.putAtPath(listOf(k), v) }
                    child
                }

                else -> value
            }
        put(key, merged)
    }
}

/**
 * Minimal JSON serializer for the specification (strings, numbers, booleans, lists, maps).
 */
internal object Json {
    fun write(value: Any?): String = StringBuilder().also { it.appendJson(value) }.toString()

    private fun StringBuilder.appendJson(value: Any?) {
        when (value) {
            null -> append("null")

            is String -> appendString(value)

            is Number, is Boolean -> append(value.toString())

            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) append(',')
                    appendString(key.toString())
                    append(':')
                    appendJson(item)
                }
                append('}')
            }

            is Iterable<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    appendJson(item)
                }
                append(']')
            }

            is Array<*> -> appendJson(value.toList())

            else -> appendString(value.toString())
        }
    }

    @Suppress("MagicNumber")
    private fun StringBuilder.appendString(value: String) {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append(String.format(Locale.ROOT, "\\u%04x", char.code)) else append(char)
            }
        }
        append('"')
    }
}
