package io.github.gciatto.python.uv

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Default values, reproducing the conventions of
 * [template-python-project-uv](https://github.com/gciatto/template-python-project-uv).
 *
 * Collection properties are initialised via `set` rather than `convention`, so that DSL methods
 * such as [PyprojectSpec.devDependency] extend the defaults instead of replacing them.
 */
internal object Conventions {
    /** The uv version downloaded when no uv is installed (updated by Renovate). */
    const val DEFAULT_UV_VERSION = "0.13.1"

    private const val DEFAULT_INDEX_URL = "https://pypi.org/simple"

    private const val DEFAULT_REQUIRES_PYTHON = ">=3.10"

    private const val RUFF_LINE_LENGTH = 100

    private val buildSystemRequires = listOf("uv_build>=0.13.0,<0.14.0")

    private val devDependencies =
        listOf(
            "coverage>=7.16.2",
            "mypy>=2.1.0",
            "poethepoet>=0.45.0",
            "pytest>=9.0.3",
            "ruff>=0.15.12",
        )

    private val minimumPythonRegex = Regex(""">=\s*3\.(\d+)""")

    fun apply(project: Project, python: PythonExtension) {
        val providers = project.providers
        val directory = project.layout.projectDirectory
        with(python) {
            uvExecutable.convention(
                providers.gradleProperty("python.uvExecutable").orElse(providers.environmentVariable("UV_EXECUTABLE")),
            )
            uvProvisioning.convention(
                providers.gradleProperty("python.uvProvisioning")
                    .map { UvProvisioning.valueOf(it.trim().uppercase()) }
                    .orElse(UvProvisioning.AUTO),
            )
            uvVersion.convention(providers.gradleProperty("python.uvVersion").orElse(DEFAULT_UV_VERSION))
            uvIndexUrl.convention(
                listOf("UV_DEFAULT_INDEX", "UV_INDEX_URL", "PIP_INDEX_URL")
                    .fold(providers.gradleProperty("python.uvIndexUrl")) { url, variable ->
                        url.orElse(providers.environmentVariable(variable))
                    }.orElse(DEFAULT_INDEX_URL),
            )
            val cacheDirectory = project.gradle.gradleUserHomeDir.resolve("caches/python-uv/uv")
            uvCacheDirectory.convention(project.layout.dir(providers.provider { cacheDirectory }))
            pythonVersion.convention(
                providers.fileContents(directory.file(".python-version")).asText.map {
                    it.lineSequence().first().trim()
                },
            )
            testedPythonVersions.convention(emptyList())
            pyprojectFile.convention(directory.file("pyproject.toml"))
            version.convention(project.provider { Pep440.from(project.version.toString()) })
            writeVersion.convention(false)
            locked.convention(
                providers.environmentVariable("CI").map {
                    it.equals("true", ignoreCase = true)
                }.orElse(false),
            )
            cleanVirtualEnv.convention(false)
        }
        applyToPyproject(project, python.pyproject)
        with(python.lifecycle) {
            classes.set(listOf("compile"))
            test.set(listOf("test"))
            check.set(listOf("static-checks"))
            format.set(listOf("static-fixes"))
            coverage.set(listOf("coverage", "coverage-report"))
        }
        val scriptsInFile =
            providers.fileContents(python.pyprojectFile).asText.map { PyprojectReader(it).scripts.firstOrNull() }
        python.runScript.convention(python.pyproject.scripts.map { it.keys.firstOrNull() }.orElse(scriptsInFile))
        applyToPublishing(project, python.publishing)
    }

    private fun applyToPyproject(project: Project, spec: PyprojectSpec) {
        val directory = project.layout.projectDirectory
        fun exists(path: String) = directory.file(path).asFile.exists()
        with(spec) {
            name.convention(project.name)
            description.convention(project.provider { project.description })
            readme.convention(project.provider { "README.md".takeIf(::exists) })
            requiresPython.convention(DEFAULT_REQUIRES_PYTHON)
            moduleName.convention(name.map { it.replace(Regex("[^A-Za-z0-9]+"), "_").lowercase() })
            moduleRoot.convention("")
            buildBackend.convention("uv_build")
            buildSystemRequires.set(Conventions.buildSystemRequires)
            dependencies.set(emptyList())
            dependencyGroups.set(mapOf(PyprojectSpec.DEV_GROUP to devDependencies))
            val modulePath = moduleRoot.zip(moduleName) { root, module ->
                listOf(root, module).filter(String::isNotBlank).joinToString("/")
            }
            scripts.set(
                modulePath.zip(moduleName) { path, module ->
                    if (exists("$path/__main__.py")) mapOf(module to "$module.__main__:main") else emptyMap()
                },
            )
            poeTasks.set(modulePath.map { path -> defaultPoeTasks(path, ::exists) })
            tools.set(
                requiresPython.map { requirement ->
                    val ruff = linkedMapOf<String, Any>()
                    minimumPythonRegex.find(requirement)?.let { ruff["target-version"] = "py3${it.groupValues[1]}" }
                    ruff["line-length"] = RUFF_LINE_LENGTH
                    // Gradle's build directory contains staged copies of the sources
                    ruff["extend-exclude"] = listOf("build")
                    linkedMapOf("pytest.ini_options" to mapOf("testpaths" to listOf("tests")), "ruff" to ruff)
                },
            )
        }
    }

    private fun defaultPoeTasks(modulePath: String, exists: (String) -> Boolean): Map<String, Any> {
        val checkedPaths = (listOf(modulePath) + listOf("tests", "scripts").filter(exists)).joinToString(" ")
        return buildMap {
            put("test", "pytest")
            put("coverage", "coverage run -m pytest")
            put("coverage-report", "coverage report -m")
            put("coverage-html", "coverage html")
            put("ruff-check", "ruff check")
            put("ruff-fix", "ruff check --fix")
            put("format", "ruff format")
            put("format-check", "ruff format --check")
            put("mypy", "mypy $checkedPaths")
            put("compile", "python -m compileall -q $checkedPaths")
            put("static-checks", listOf("compile", "format-check", "ruff-check", "mypy"))
            put("static-fixes", listOf("ruff-fix", "format"))
            if (exists("scripts/githooks.py")) {
                put("install-hooks", "python scripts/githooks.py install")
                put("uninstall-hooks", "python scripts/githooks.py uninstall")
            }
        }
    }

    private fun applyToPublishing(project: Project, publishing: PythonPublishingSpec) {
        val providers = project.providers
        publishing.defaultRepository.convention(PythonRepository.PYPI)
        publishing.dryRun.convention(
            providers.gradleProperty("python.publish.dryRun").map(String::toBoolean).orElse(false),
        )
        publishing.repositories.configureEach { repository ->
            val variables =
                listOf(
                    repository.name.uppercase().replace(Regex("[^A-Z0-9]+"), "_") + "_TOKEN",
                    // conventional name used by template-python-project-uv
                    if (repository.name == PythonRepository.TEST_PYPI) "TEST_PYPI_TOKEN" else null,
                ).filterNotNull()
            val fromEnvironment: Provider<String> =
                variables
                    .map(providers::environmentVariable)
                    .reduce { first, second -> first.orElse(second) }
            repository.token.convention(fromEnvironment.orElse(providers.gradleProperty("${repository.name}Token")))
        }
        publishing.repositories.register(PythonRepository.PYPI)
        publishing.repositories.register(PythonRepository.TEST_PYPI) {
            it.url.convention("https://test.pypi.org/simple/")
            it.publishUrl.convention("https://test.pypi.org/legacy/")
        }
    }
}
