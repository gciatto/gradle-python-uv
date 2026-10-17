package io.github.gciatto.python.uv.provisioning

/**
 * Selects the uv wheel matching a [HostPlatform], among the files of a release.
 * Wheels are named `uv-<version>-py3-none-<platform tags>.whl`, where platform tags are dot-separated.
 */
internal object UvWheels {
    private val glibcTag = Regex("""manylinux_(\d+)_(\d+)_(\w+)""")

    private val legacyGlibcTags = mapOf(
        "manylinux2014" to (2 to 17),
        "manylinux2010" to (2 to 12),
        "manylinux1" to (2 to 5),
    )

    private val muslTag = Regex("""musllinux_(\d+)_(\d+)_(\w+)""")

    private val macTag = Regex("""macosx_\d+_\d+_(\w+)""")

    private val windowsTags = mapOf("x86_64" to "win_amd64", "aarch64" to "win_arm64", "i686" to "win32")

    /** Returns the file name of the wheel to use, or null if none matches [platform]. */
    fun select(fileNames: Iterable<String>, version: String, platform: HostPlatform): String? = fileNames
        .filter { it.startsWith("uv-$version-") && it.endsWith(".whl") }
        .mapNotNull { name -> requirement(platformTags(name), platform)?.let { name to it } }
        .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
        ?.first

    private fun platformTags(fileName: String): List<String> =
        fileName.removeSuffix(".whl").split('-').last().split('.')

    /** The minimum libc version required by the best-matching tag (lower is more compatible), or null. */
    private fun requirement(tags: List<String>, platform: HostPlatform): Int? = tags.mapNotNull { tag ->
        when (platform.os) {
            HostPlatform.Os.WINDOWS -> 0.takeIf { tag == windowsTags[platform.arch] }

            HostPlatform.Os.MACOS -> macTag.matchEntire(tag)?.groupValues?.get(1)
                ?.takeIf { it == "universal2" || it == macArch(platform.arch) }
                ?.let { 0 }

            HostPlatform.Os.LINUX -> if (platform.musl) {
                muslRequirement(
                    tag,
                    platform.arch,
                )
            } else {
                glibcRequirement(tag, platform.arch)
            }

            HostPlatform.Os.OTHER -> null
        }
    }.minOrNull()

    private fun macArch(arch: String) = if (arch == "aarch64") "arm64" else arch

    private fun glibcRequirement(tag: String, arch: String): Int? {
        glibcTag.matchEntire(tag)?.let { match ->
            val (major, minor, tagArch) = match.destructured
            return version(major.toInt(), minor.toInt()).takeIf { tagArch == arch }
        }
        return legacyGlibcTags.entries
            .firstOrNull { (prefix, _) -> tag == "${prefix}_$arch" }
            ?.value
            ?.let { (major, minor) -> version(major, minor) }
    }

    private fun muslRequirement(tag: String, arch: String): Int? = muslTag.matchEntire(tag)?.let { match ->
        val (major, minor, tagArch) = match.destructured
        version(major.toInt(), minor.toInt()).takeIf { tagArch == arch }
    }

    @Suppress("MagicNumber")
    private fun version(major: Int, minor: Int) = major * 1000 + minor
}
