package io.github.gciatto.python.uv

/**
 * Converts Gradle-style (semantic) versions, such as the ones produced by
 * [git-sensitive-semantic-versioning](https://github.com/DanySK/git-sensitive-semantic-versioning-gradle-plugin),
 * into [PEP 440](https://peps.python.org/pep-0440/) versions.
 *
 * Examples: `1.2.3` → `1.2.3`, `1.2.4-dev05-abc1234` → `1.2.4.dev5`, `2.0.0-rc.1` → `2.0.0rc1`,
 * `1.0.0-alpha.2-dev01+abc` → `1.0.0a2.dev1`.
 * Local version labels (e.g. commit hashes) are dropped unless `includeLocal` is true,
 * because PyPI rejects them.
 */
object Pep440 {
    private const val DEFAULT_VERSION = "0.0.0"

    private val versionRegex = Regex("""^[vV]?(\d+(?:\.\d+)*)(?:[-.+_]?(.*))?$""")

    private val tokenRegex = Regex("""^([a-zA-Z]+)(\d*)$""")

    private val preReleaseKinds =
        mapOf(
            "a" to "a",
            "alpha" to "a",
            "b" to "b",
            "beta" to "b",
            "c" to "rc",
            "rc" to "rc",
            "pre" to "rc",
            "preview" to "rc",
        )

    private val devKinds = setOf("dev", "snapshot")

    private val postKinds = setOf("post", "rev", "r")

    /**
     * Converts [version] into a PEP 440 compliant version string.
     */
    @JvmStatic
    @JvmOverloads
    fun from(version: String, includeLocal: Boolean = false): String {
        val match = versionRegex.matchEntire(version.trim()) ?: return DEFAULT_VERSION
        val release = match.groupValues[1]
        val tokens =
            match.groupValues[2]
                .split('-', '.', '+', '_')
                .filter { it.isNotBlank() }
        val result = StringBuilder(release)
        val local = mutableListOf<String>()
        var pendingKind: String? = null
        var devSeen = false
        for (token in tokens) {
            val parsed = tokenRegex.matchEntire(token)
            val kind = parsed?.groupValues?.get(1)?.lowercase()
            val number = parsed?.groupValues?.get(2)
            when {
                // anything after the dev segment (e.g. a commit hash) is local
                devSeen && pendingKind == null -> local += token.lowercase()

                pendingKind != null && token.all(Char::isDigit) -> {
                    result.append(pendingKind).append(token.toInt())
                    pendingKind = null
                }

                kind != null && kind in preReleaseKinds -> {
                    flush(result, pendingKind)
                    pendingKind = appendOrDefer(result, preReleaseKinds.getValue(kind), number)
                }

                kind != null && kind in devKinds -> {
                    flush(result, pendingKind)
                    devSeen = true
                    pendingKind = appendOrDefer(result, ".dev", number)
                }

                kind != null && kind in postKinds -> {
                    flush(result, pendingKind)
                    pendingKind = appendOrDefer(result, ".post", number)
                }

                else -> {
                    flush(result, pendingKind)
                    pendingKind = null
                    local += token.lowercase()
                }
            }
        }
        flush(result, pendingKind)
        if (includeLocal && local.isNotEmpty()) {
            result.append('+').append(local.joinToString("."))
        }
        return result.toString()
    }

    private fun appendOrDefer(builder: StringBuilder, kind: String, number: String?): String? =
        if (number.isNullOrEmpty()) {
            kind
        } else {
            builder.append(kind).append(number.toInt())
            null
        }

    private fun flush(builder: StringBuilder, pendingKind: String?) {
        if (pendingKind != null) builder.append(pendingKind).append('0')
    }
}
