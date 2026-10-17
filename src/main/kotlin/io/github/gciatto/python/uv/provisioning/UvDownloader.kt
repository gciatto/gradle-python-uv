package io.github.gciatto.python.uv.provisioning

import java.io.File
import java.io.RandomAccessFile
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Downloads uv from a Python package index, as the platform-specific wheel uv publishes on PyPI.
 *
 * The index is queried via the PEP 503 "simple" API (supported by PyPI mirrors and proxies),
 * the wheel is verified against the SHA-256 the index publishes, and the executable is extracted into
 * `<cacheDirectory>/<version>/<platform>/`, which is shared by all builds of the same user.
 *
 * @property indexUrl the base URL of the simple index (e.g. `https://pypi.org/simple`)
 * @property cacheDirectory where uv versions are installed
 * @property logger where progress is reported
 */
internal class UvDownloader(
    private val indexUrl: String,
    private val cacheDirectory: File,
    private val logger: Logger,
) {
    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build()
    }

    /** Returns the uv executable of [version] for [platform], downloading it if necessary. */
    fun install(version: String, platform: HostPlatform): File {
        val directory = cacheDirectory.resolve(version).resolve(platform.toString())
        val executable = directory.resolve(platform.executableName)
        if (executable.isFile) return executable
        directory.mkdirs()
        return withLock(directory.resolveSibling("${directory.name}.lock")) {
            if (!executable.isFile) download(version, platform, executable)
            executable
        }
    }

    private fun download(version: String, platform: HostPlatform, target: File) {
        val links = fetchLinks()
        val wheel = UvWheels.select(links.keys, version, platform)
            ?: throw GradleException(
                "No uv $version wheel for platform $platform in $indexUrl: " +
                    "install uv manually, or set python.uvExecutable",
            )
        val url = links.getValue(wheel)
        val expectedHash = url.fragment?.removePrefix("sha256=")?.takeIf { url.fragment.startsWith("sha256=") }
            ?: throw GradleException("The index $indexUrl publishes no SHA-256 for $wheel: refusing to install it")
        logger.lifecycle("Downloading uv {} for {} from {}", version, platform, url.host)
        val archive = File.createTempFile("uv-", ".whl", target.parentFile)
        try {
            get(
                URI(url.scheme, url.authority, url.path, url.query, null),
                HttpResponse.BodyHandlers.ofFile(archive.toPath()),
            )
            val actualHash = sha256(archive)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                throw GradleException("Checksum mismatch for $wheel: expected $expectedHash, got $actualHash")
            }
            extract(archive, "uv-$version.data/scripts/${platform.executableName}", target)
        } finally {
            archive.delete()
        }
        logger.lifecycle("Installed uv {} into {}", version, target)
    }

    /** Returns the links of the uv project page, indexed by file name. */
    private fun fetchLinks(): Map<String, URI> {
        val page = URI("${indexUrl.trimEnd('/')}/uv/")
        val html = get(page, HttpResponse.BodyHandlers.ofString())
        return anchorRegex.findAll(html).associate { match ->
            val (href, text) = match.destructured
            text.trim() to page.resolve(href.replace("&amp;", "&"))
        }
    }

    private fun <T> get(uri: URI, handler: HttpResponse.BodyHandler<T>): T {
        val request = HttpRequest.newBuilder(uri)
            .header("Accept", "text/html")
            .header("User-Agent", "gradle-python-uv")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .GET()
            .build()
        val response = try {
            client.send(request, handler)
        } catch (e: java.io.IOException) {
            throw GradleException("Cannot download $uri: ${e.message}", e)
        }
        if (response.statusCode() !in HTTP_OK) {
            throw GradleException("Cannot download $uri: HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    private fun extract(archive: File, entryName: String, target: File) {
        val temporary = File.createTempFile("uv-", ".tmp", target.parentFile)
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(entryName) ?: throw GradleException("$entryName not found in ${archive.name}")
            zip.getInputStream(entry).use { input -> temporary.outputStream().use { input.copyTo(it) } }
        }
        temporary.setExecutable(true, false)
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    private companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val REQUEST_TIMEOUT_SECONDS = 600L
        private val HTTP_OK = 200..299
        private val anchorRegex = Regex("""<a\s[^>]*href="([^"]+)"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)

        /** Serialises installations within this JVM (file locks are per process). */
        private val jvmLock = Any()

        /** Serialises installations across processes (e.g. concurrent Gradle daemons). */
        private fun <T> withLock(lockFile: File, action: () -> T): T = synchronized(jvmLock) {
            RandomAccessFile(lockFile, "rw").use { file -> file.channel.lock().use { action() } }
        }
    }
}
