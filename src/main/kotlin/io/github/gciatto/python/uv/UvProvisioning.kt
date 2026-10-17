package io.github.gciatto.python.uv

/**
 * How the plugin obtains the uv executable.
 */
enum class UvProvisioning {
    /** Use an installed uv if one is found, otherwise download the pinned version. */
    AUTO,

    /** Always use the pinned version, downloaded into the Gradle user home (reproducible builds). */
    ALWAYS,

    /** Never download uv: fail if no installed uv is found (offline or locked-down machines). */
    NEVER,
}
