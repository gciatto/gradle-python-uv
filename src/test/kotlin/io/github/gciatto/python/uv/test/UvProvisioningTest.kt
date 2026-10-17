package io.github.gciatto.python.uv.test

import io.github.gciatto.python.uv.UvProvisioning
import io.github.gciatto.python.uv.provisioning.HostPlatform
import io.github.gciatto.python.uv.provisioning.HostPlatform.Os
import io.github.gciatto.python.uv.provisioning.UvLocator
import io.github.gciatto.python.uv.provisioning.UvResolver
import io.github.gciatto.python.uv.provisioning.UvWheels
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.io.path.createTempDirectory
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging

class UvProvisioningTest :
    StringSpec({
        val wheels = listOf(
            "uv-0.13.1-py3-none-linux_armv6l.whl",
            "uv-0.13.1-py3-none-macosx_10_12_x86_64.whl",
            "uv-0.13.1-py3-none-macosx_11_0_arm64.whl",
            "uv-0.13.1-py3-none-manylinux_2_17_aarch64.manylinux2014_aarch64.musllinux_1_1_aarch64.whl",
            "uv-0.13.1-py3-none-manylinux_2_17_armv7l.manylinux2014_armv7l.musllinux_1_1_armv7l.whl",
            "uv-0.13.1-py3-none-manylinux_2_17_armv7l.manylinux2014_armv7l.whl",
            "uv-0.13.1-py3-none-manylinux_2_17_i686.manylinux2014_i686.whl",
            "uv-0.13.1-py3-none-manylinux_2_17_x86_64.manylinux2014_x86_64.whl",
            "uv-0.13.1-py3-none-manylinux_2_28_aarch64.whl",
            "uv-0.13.1-py3-none-musllinux_1_1_x86_64.whl",
            "uv-0.13.1-py3-none-win32.whl",
            "uv-0.13.1-py3-none-win_amd64.whl",
            "uv-0.13.1-py3-none-win_arm64.whl",
            "uv-0.13.1.tar.gz",
            "uv-0.13.0-py3-none-win_amd64.whl",
        )

        "os.name and os.arch values should be normalised" {
            HostPlatform.parseOs("Windows 11") shouldBe Os.WINDOWS
            HostPlatform.parseOs("Mac OS X") shouldBe Os.MACOS
            HostPlatform.parseOs("Linux") shouldBe Os.LINUX
            HostPlatform.parseOs("FreeBSD") shouldBe Os.OTHER
            HostPlatform.normaliseArch("amd64") shouldBe "x86_64"
            HostPlatform.normaliseArch("arm64") shouldBe "aarch64"
            HostPlatform.normaliseArch("x86") shouldBe "i686"
            HostPlatform.normaliseArch("arm") shouldBe "armv7l"
        }

        "the most compatible wheel should be selected for each platform" {
            mapOf(
                HostPlatform(Os.LINUX, "x86_64") to "manylinux_2_17_x86_64.manylinux2014_x86_64",
                HostPlatform(Os.LINUX, "aarch64") to
                    "manylinux_2_17_aarch64.manylinux2014_aarch64.musllinux_1_1_aarch64",
                HostPlatform(Os.LINUX, "i686") to "manylinux_2_17_i686.manylinux2014_i686",
                HostPlatform(Os.LINUX, "x86_64", musl = true) to "musllinux_1_1_x86_64",
                HostPlatform(Os.LINUX, "aarch64", musl = true) to
                    "manylinux_2_17_aarch64.manylinux2014_aarch64.musllinux_1_1_aarch64",
                HostPlatform(Os.MACOS, "x86_64") to "macosx_10_12_x86_64",
                HostPlatform(Os.MACOS, "aarch64") to "macosx_11_0_arm64",
                HostPlatform(Os.WINDOWS, "x86_64") to "win_amd64",
                HostPlatform(Os.WINDOWS, "aarch64") to "win_arm64",
                HostPlatform(Os.WINDOWS, "i686") to "win32",
            ).forEach { (platform, tags) ->
                UvWheels.select(wheels, "0.13.1", platform) shouldBe "uv-0.13.1-py3-none-$tags.whl"
            }
            UvWheels.select(wheels, "0.13.1", HostPlatform(Os.OTHER, "x86_64")) shouldBe null
            UvWheels.select(wheels, "9.9.9", HostPlatform(Os.LINUX, "x86_64")) shouldBe null
        }

        "uv should be found in PATH, case-insensitively and with the platform's separator" {
            val root = createTempDirectory("uv-locator").toFile()
            val bin = root.resolve("some/bin").apply { mkdirs() }
            val exe = bin.resolve("uv.exe").apply { writeText("") }
            val windows = HostPlatform(Os.WINDOWS, "x86_64")
            val locator = UvLocator(windows, mapOf("Path" to "C:\\nowhere;\"${bin.path}\""), root.resolve("home"))
            locator.find() shouldBe exe.absoluteFile
        }

        "uv should be found in well-known locations, when missing from PATH" {
            val home = createTempDirectory("uv-home").toFile()
            val uv = home.resolve(".local/bin/uv").apply {
                parentFile.mkdirs()
                writeText("#!/bin/sh\n")
                setExecutable(true)
            }
            val linux = HostPlatform(Os.LINUX, "x86_64")
            val locator = UvLocator(linux, mapOf("PATH" to "/nonexistent"), home, systemDirectories = emptyList())
            locator.find() shouldBe uv.absoluteFile
            // non-executable files are ignored
            uv.setExecutable(false)
            locator.find() shouldBe null
        }

        "explicit executables may be paths or command names" {
            val root = createTempDirectory("uv-explicit").toFile()
            val custom = root.resolve("tools/my-uv").apply {
                parentFile.mkdirs()
                writeText("#!/bin/sh\n")
                setExecutable(true)
            }
            val linux = HostPlatform(Os.LINUX, "x86_64")
            val locator = UvLocator(linux, mapOf("PATH" to custom.parent), root.resolve("home"))
            locator.resolve("tools/my-uv", root) shouldBe custom
            locator.resolve(custom.absolutePath, File("/")) shouldBe custom
            locator.resolve("my-uv", root) shouldBe custom.absoluteFile
            locator.resolve("missing-uv", root) shouldBe null
        }

        "NEVER should fail with a helpful message when uv is not installed" {
            val home = createTempDirectory("uv-never").toFile()
            val request = UvResolver.Request(
                explicitExecutable = null,
                provisioning = UvProvisioning.NEVER,
                version = "0.13.1",
                indexUrl = "https://pypi.org/simple",
                cacheDirectory = home.resolve("cache"),
                baseDirectory = home,
            )
            val error = shouldThrow<GradleException> {
                UvResolver.resolve(
                    request,
                    Logging.getLogger(UvProvisioningTest::class.java),
                    HostPlatform(Os.LINUX, "x86_64"),
                    UvLocator(HostPlatform(Os.LINUX, "x86_64"), mapOf("PATH" to "/nonexistent"), home, emptyList()),
                )
            }
            error.message shouldContain "python.uvProvisioning is NEVER"
        }
    })
