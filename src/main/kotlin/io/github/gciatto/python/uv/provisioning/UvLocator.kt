package io.github.gciatto.python.uv.provisioning

import java.io.File

/**
 * Looks for an installed uv executable, independently of the `PATH` the Gradle daemon was started with:
 * besides `PATH`, it checks the locations where the official installers and package managers put uv
 * (which may be missing from the `PATH` of IDEs launched from the desktop).
 *
 * @property platform the host platform
 * @property environment the environment variables (looked up case-insensitively, as on Windows)
 * @property home the user's home directory
 * @property systemDirectories system-wide installation directories (e.g. Homebrew's), checked last
 */
internal class UvLocator(
    private val platform: HostPlatform,
    environment: Map<String, String>,
    private val home: File,
    private val systemDirectories: List<File> = defaultSystemDirectories(platform),
) {
    private val environment: Map<String, String> =
        environment.entries.associate { (key, value) -> key.uppercase() to value }

    private fun env(name: String): File? = environment[name]?.takeIf { it.isNotBlank() }?.let(::File)

    /** The directories listed in `PATH`. */
    val pathDirectories: List<File>
        get() = environment["PATH"].orEmpty()
            .split(platform.pathSeparator)
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
            .map(::File)

    /** Well-known installation directories, in order of preference. */
    val wellKnownDirectories: List<File>
        get() = buildList {
            env("UV_INSTALL_DIR")?.let(::add)
            env("XDG_BIN_HOME")?.let(::add)
            env("XDG_DATA_HOME")?.let { add(it.resolve("../bin").normalize()) }
            add(home.resolve(".local/bin"))
            env("CARGO_HOME")?.let { add(it.resolve("bin")) }
            add(home.resolve(".cargo/bin"))
            if (platform.isWindows) {
                env("LOCALAPPDATA")?.let { add(it.resolve("Microsoft/WinGet/Links")) }
                env("SCOOP")?.let { add(it.resolve("shims")) }
                add(home.resolve("scoop/shims"))
                env("PROGRAMDATA")?.let { add(it.resolve("chocolatey/bin")) }
            }
            addAll(systemDirectories)
        }.distinct()

    /**
     * Resolves [executable], which is either a path (absolute, or relative to [baseDirectory]),
     * or a bare command name looked up in `PATH` and in the [wellKnownDirectories].
     */
    fun resolve(executable: String, baseDirectory: File): File? {
        val looksLikePath = '/' in executable || '\\' in executable || File(executable).isAbsolute
        return if (looksLikePath) {
            val file = File(executable).let { if (it.isAbsolute) it else baseDirectory.resolve(executable) }
            (sequenceOf(file) + sequenceOf(File(platform.executable(file.path))))
                .firstOrNull(::isExecutable)
        } else {
            find(platform.executable(executable))
        }
    }

    /** Finds the uv executable in `PATH` or in the [wellKnownDirectories]. */
    fun find(name: String = platform.executableName): File? = (pathDirectories + wellKnownDirectories).asSequence()
        .map { it.resolve(name) }
        .firstOrNull(::isExecutable)
        ?.absoluteFile

    private fun isExecutable(file: File): Boolean = file.isFile && (platform.isWindows || file.canExecute())

    /** Default system-wide directories. */
    companion object {
        /** The system-wide directories where package managers install uv on [platform]. */
        fun defaultSystemDirectories(platform: HostPlatform): List<File> = when (platform.os) {
            HostPlatform.Os.WINDOWS -> emptyList()
            HostPlatform.Os.MACOS -> listOf("/opt/homebrew/bin", "/usr/local/bin", "/opt/local/bin")
            else -> listOf("/home/linuxbrew/.linuxbrew/bin", "/usr/local/bin", "/usr/bin", "/snap/bin")
        }.map(::File)
    }
}
