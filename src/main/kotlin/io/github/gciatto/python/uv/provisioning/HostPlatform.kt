package io.github.gciatto.python.uv.provisioning

import java.io.File

/**
 * The operating system and CPU architecture uv runs on.
 *
 * @property os the operating system family
 * @property arch the normalised CPU architecture (e.g. `x86_64`, `aarch64`, `i686`, `armv7l`)
 * @property musl whether the C library is musl (Linux only)
 */
internal data class HostPlatform(val os: Os, val arch: String, val musl: Boolean = false) {
    /** Operating system families. */
    enum class Os { LINUX, MACOS, WINDOWS, OTHER }

    /** Whether this is Windows. */
    val isWindows: Boolean get() = os == Os.WINDOWS

    /** The name of the uv executable on this platform. */
    val executableName: String get() = executable("uv")

    /** The separator of entries in `PATH`. */
    val pathSeparator: Char get() = if (isWindows) ';' else ':'

    /** The file name of executable [name] on this platform. */
    fun executable(name: String): String = if (isWindows &&
        !name.endsWith(".exe", ignoreCase = true)
    ) {
        "$name.exe"
    } else {
        name
    }

    override fun toString(): String = "${os.name.lowercase()}-$arch${if (musl) "-musl" else ""}"

    companion object {
        /** The platform of the running JVM. */
        val current: HostPlatform by lazy {
            val os = parseOs(System.getProperty("os.name").orEmpty())
            HostPlatform(os, normaliseArch(System.getProperty("os.arch").orEmpty()), os == Os.LINUX && isMusl())
        }

        /** Maps `os.name` values onto [Os]. */
        fun parseOs(name: String): Os = name.lowercase().let {
            when {
                it.startsWith("windows") -> Os.WINDOWS
                it.startsWith("mac") || it.startsWith("darwin") -> Os.MACOS
                it.startsWith("linux") -> Os.LINUX
                else -> Os.OTHER
            }
        }

        /** Maps `os.arch` values onto the architecture names used by Python platform tags. */
        fun normaliseArch(arch: String): String = when (val value = arch.lowercase()) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            "x86", "i386", "i486", "i586", "i686" -> "i686"
            "arm", "armv7", "armv7l", "armhf" -> "armv7l"
            "ppc64le", "powerpc64le" -> "ppc64le"
            else -> value
        }

        private fun isMusl(): Boolean = File("/lib").listFiles { file -> file.name.startsWith("ld-musl-") }
            ?.isNotEmpty() == true
    }
}
