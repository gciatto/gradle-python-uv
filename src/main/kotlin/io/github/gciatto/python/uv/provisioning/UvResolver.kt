package io.github.gciatto.python.uv.provisioning

import io.github.gciatto.python.uv.UvProvisioning
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Resolves the uv executable to use, according to the following strategy:
 * 1. the explicitly configured executable (path or command name), if any;
 * 2. unless [UvProvisioning.ALWAYS], an installed uv found in `PATH` or in well-known locations;
 * 3. unless [UvProvisioning.NEVER], the pinned uv version, downloaded into the cache directory.
 *
 * Results are memoised per JVM, so the lookup (and the download) happens at most once per configuration.
 */
internal object UvResolver {
    /**
     * The inputs of the resolution.
     *
     * @property explicitExecutable the configured executable, if any
     * @property provisioning the provisioning mode
     * @property version the uv version to download
     * @property indexUrl the index uv is downloaded from
     * @property cacheDirectory where downloaded uv versions are stored
     * @property baseDirectory the directory relative executable paths are resolved against
     */
    data class Request(
        val explicitExecutable: String?,
        val provisioning: UvProvisioning,
        val version: String,
        val indexUrl: String,
        val cacheDirectory: File,
        val baseDirectory: File,
    )

    private val cache = ConcurrentHashMap<Request, File>()

    /** Resolves the uv executable for [request]. */
    fun resolve(
        request: Request,
        logger: Logger,
        platform: HostPlatform = HostPlatform.current,
        locator: UvLocator = UvLocator(platform, System.getenv(), File(System.getProperty("user.home"))),
    ): File = cache[request]?.takeIf { it.isFile } ?: doResolve(request, logger, platform, locator).also {
        logger.info("Using uv executable {}", it)
        cache[request] = it
    }

    private fun doResolve(request: Request, logger: Logger, platform: HostPlatform, locator: UvLocator): File {
        val explicit = request.explicitExecutable?.takeIf { it.isNotBlank() }
        val installed = if (explicit == null && request.provisioning != UvProvisioning.ALWAYS) locator.find() else null
        return when {
            explicit != null -> locator.resolve(explicit, request.baseDirectory) ?: throw GradleException(
                "The configured uv executable '$explicit' was not found " +
                    "(searched PATH and ${locator.wellKnownDirectories.joinToString()})",
            )

            installed != null -> installed

            request.provisioning == UvProvisioning.NEVER -> throw GradleException(
                "uv was not found in PATH, nor in ${locator.wellKnownDirectories.joinToString()}, " +
                    "and python.uvProvisioning is NEVER: " +
                    "install uv (https://docs.astral.sh/uv/getting-started/installation/), " +
                    "set python.uvExecutable, or allow downloads with python.uvProvisioning = AUTO",
            )

            else -> UvDownloader(request.indexUrl, request.cacheDirectory, logger).install(request.version, platform)
        }
    }
}
