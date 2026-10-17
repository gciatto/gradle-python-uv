package io.github.gciatto.python.uv

import io.github.gciatto.python.uv.tasks.PoeTask
import io.github.gciatto.python.uv.tasks.SyncPyproject
import io.github.gciatto.python.uv.tasks.UvBuild
import io.github.gciatto.python.uv.tasks.UvExec
import io.github.gciatto.python.uv.tasks.UvPublish
import io.github.gciatto.python.uv.tasks.UvRun
import io.github.gciatto.python.uv.tasks.UvSync
import io.github.gciatto.python.uv.tasks.UvTask
import java.util.concurrent.Callable
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Gradle plugin operating Python projects via [uv](https://docs.astral.sh/uv/) and
 * [Poe the Poet](https://poethepoet.natn.io/), following the conventions of
 * [template-python-project-uv](https://github.com/gciatto/template-python-project-uv).
 *
 * Gradle lifecycle tasks (`classes`, `test`, `check`, `assemble`, `build`, `run`, `publish`, `clean`)
 * are mapped onto uv commands and Poe tasks, so that Python projects can be part of
 * multi-platform Gradle builds (e.g. along with [kt-mpp](https://github.com/gciatto/kt-mpp)).
 */
class PythonUvPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        pluginManager.apply(BasePlugin::class.java)
        pluginManager.apply(PublishingPlugin::class.java)
        val python = extensions.create(PythonExtension.NAME, PythonExtension::class.java)
        Conventions.apply(this, python)
        tasks.withType<UvTask>().configureEach {
            it.uvExecutable.convention(python.uvExecutable)
            it.workingDirectory.convention(layout.projectDirectory)
            it.environment.convention(emptyMap())
        }
        val pyproject = registerPyprojectTasks(python)
        val sync = registerEnvironmentTasks(python, pyproject)
        registerLifecycleTasks(python, sync)
        registerPublishingTasks(python, pyproject, sync)
        configureClean(python)
        afterEvaluate {
            registerPoeTasks(python, sync)
            registerBuildSources(python)
            registerMultiVersionTests(python)
        }
    }

    private fun Project.registerPyprojectTasks(python: PythonExtension): TaskProvider<SyncPyproject> {
        val specification = provider { Json.write(PyprojectSpecification.of(python)) }
        val generate =
            tasks.register<SyncPyproject>("generatePyproject") {
                group = GROUP
                description = "Merges the Gradle configuration of the Python project into pyproject.toml"
                configureSync(python, specification, checkOnly = false)
                outputs.upToDateWhen { false }
            }
        val check =
            tasks.register<SyncPyproject>("checkPyproject") {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description = "Checks that pyproject.toml is in sync with the Gradle configuration"
                configureSync(python, specification, checkOnly = true)
                pyprojectInput.set(python.pyprojectFile.filterExists())
                marker.set(layout.buildDirectory.file("python/$name.ok"))
                mustRunAfter(generate)
            }
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { it.dependsOn(check) }
        return generate
    }

    private fun SyncPyproject.configureSync(
        python: PythonExtension,
        spec: org.gradle.api.provider.Provider<String>,
        checkOnly: Boolean,
    ) {
        specification.set(spec)
        pyprojectFile.set(python.pyprojectFile)
        this.checkOnly.set(checkOnly)
        scriptRequirements.set(listOf("tomlkit>=0.13", "packaging>=24"))
        scriptDirectory.set(project.layout.buildDirectory.dir("python/pyproject-sync"))
    }

    private fun Project.registerEnvironmentTasks(
        python: PythonExtension,
        generate: TaskProvider<SyncPyproject>,
    ): TaskProvider<UvSync> {
        val directory = layout.projectDirectory
        val pyproject = python.pyprojectFile
        tasks.register<UvExec>("uvPythonInstall") {
            group = GROUP
            description = "Installs the Python version(s) used by the project via uv"
            arguments.set(
                python.pythonVersion
                    .map { listOf(it) }
                    .orElse(emptyList())
                    .zip(python.testedPythonVersions) { main, tested ->
                        listOf("python", "install") +
                            (main + tested).distinct()
                    },
            )
        }
        tasks.register<UvExec>("uvLock") {
            group = GROUP
            description = "Updates uv.lock according to pyproject.toml"
            arguments.set(listOf("lock"))
            mustRunAfter(generate)
        }
        return tasks.register<UvSync>("uvSync") {
            group = GROUP
            description = "Creates or updates the Python virtual environment (uv sync)"
            pyprojectFile.set(python.pyprojectFile)
            pyprojectInput.set(python.pyprojectFile.filterExists())
            lockFile.set(provider { directory.file("uv.lock") }.filterExists())
            pythonVersionFile.set(provider { directory.file(".python-version") }.filterExists())
            pythonVersion.set(python.pythonVersion)
            locked.set(python.locked)
            marker.set(directory.file(".venv/.gradle-uv-sync"))
            mustRunAfter(generate)
            // bootstrap: if pyproject.toml does not exist yet, generate it from the Gradle configuration
            dependsOn(Callable { if (pyproject.get().asFile.exists()) emptyList() else listOf(generate) })
        }
    }

    private fun Project.registerLifecycleTasks(python: PythonExtension, sync: TaskProvider<UvSync>) {
        val classes =
            lifecycleTask(
                "classes",
                BUILD_GROUP,
                "Compiles Python sources (Poe task(s) ${python.lifecycle.classes.get()})",
            )
        lifecycleTask("testClasses", BUILD_GROUP, "Compiles Python sources and tests").configure {
            it.dependsOn(classes)
        }
        // as for JVM projects, where assemble → jar → classes
        tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) { it.dependsOn(classes) }
        val test =
            lifecycleTask("test", VERIFICATION_GROUP, "Runs Python tests (Poe task(s) ${python.lifecycle.test.get()})")
        test.configure { it.mustRunAfter(classes) }
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { it.dependsOn(test) }
        lifecycleTask("format", GROUP, "Fixes Python sources (Poe task(s) ${python.lifecycle.format.get()})")
        lifecycleTask(
            "coverage",
            VERIFICATION_GROUP,
            "Runs tests with coverage (Poe task(s) ${python.lifecycle.coverage.get()})",
        )
        val run =
            tasks.register<UvRun>(if ("run" in tasks.names) "runPython" else "run") {
                group = "application"
                description = "Runs the Python application (uv run)"
                script.set(python.runScript)
                module.set(python.pyproject.moduleName)
                applicationArguments.convention(emptyList())
                dependsOn(sync)
            }
        if (run.name != "run") tasks.named("run") { it.dependsOn(run) }
    }

    private fun Project.lifecycleTask(name: String, group: String, description: String): TaskProvider<Task> =
        if (name in tasks.names) {
            tasks.named(name)
        } else {
            tasks.register(name) {
                it.group = group
                it.description = description
            }
        }

    private fun Project.registerPoeTasks(python: PythonExtension, sync: TaskProvider<UvSync>) {
        val fromFile = PyprojectReader(providers.fileContents(python.pyprojectFile).asText.orNull)
        val names = (python.pyproject.poeTasks.get().keys + fromFile.poeTasks + python.lifecycle.allPoeTasks).distinct()
        for (poeName in names) {
            tasks.register<PoeTask>(poeTaskName(poeName)) {
                group = GROUP
                description = "Runs the '$poeName' Poe task (uv run poe $poeName)"
                poeTask.set(poeName)
                extraArguments.convention(emptyList())
                dependsOn(sync)
            }
        }
        with(python.lifecycle) {
            wire("classes", classes.get())
            wire("test", test.get())
            wire(LifecycleBasePlugin.CHECK_TASK_NAME, check.get())
            wire("format", format.get())
            wire("coverage", coverage.get())
        }
    }

    private fun Project.wire(lifecycleTaskName: String, poeNames: List<String>) {
        val poeTasks = poeNames.map(::poeTaskName)
        tasks.named(lifecycleTaskName) { it.dependsOn(poeTasks) }
        poeTasks.zipWithNext { previous, next -> tasks.named(next) { it.mustRunAfter(previous) } }
    }

    private fun Project.registerBuildSources(python: PythonExtension) {
        val modulePath =
            listOf(python.pyproject.moduleRoot.get(), python.pyproject.moduleName.get())
                .filter { it.isNotBlank() }
                .joinToString("/")
        val pyprojectName = python.pyprojectFile.get().asFile.name
        tasks.named<UvBuild>("uvBuild") {
            sources.from(
                fileTree(projectDir) { tree ->
                    tree.include(pyprojectName, "uv.lock", ".python-version", "README*", "LICENSE*")
                    tree.include("$modulePath/**")
                    tree.exclude("**/__pycache__/**", "**/*.pyc")
                },
            )
        }
    }

    private fun Project.registerMultiVersionTests(python: PythonExtension) {
        val versions = python.testedPythonVersions.get()
        if (versions.isEmpty()) return
        val testNames = python.lifecycle.test.get()
        val all =
            tasks.register("testAllPythonVersions") {
                it.group = VERIFICATION_GROUP
                it.description = "Runs Python tests against all versions in $versions"
            }
        for (version in versions) {
            for (testName in testNames) {
                val suffix = if (testNames.size > 1) poeTaskName(testName).removePrefix("poe") else ""
                val task =
                    tasks.register<PoeTask>("testPython${version.replace('.', '_')}$suffix") {
                        group = VERIFICATION_GROUP
                        description = "Runs the '$testName' Poe task with Python $version, in a dedicated environment"
                        poeTask.set(testName)
                        pythonVersion.set(version)
                        extraArguments.convention(emptyList())
                        environment.put(
                            "UV_PROJECT_ENVIRONMENT",
                            layout.buildDirectory
                                .dir("python/venvs/$version")
                                .get()
                                .asFile.absolutePath,
                        )
                    }
                all.configure { it.dependsOn(task) }
            }
        }
    }

    private fun Project.registerPublishingTasks(
        python: PythonExtension,
        generate: TaskProvider<SyncPyproject>,
        sync: TaskProvider<UvSync>,
    ) {
        val pyproject = python.pyprojectFile
        val build =
            tasks.register<UvBuild>("uvBuild") {
                group = BUILD_GROUP
                description = "Builds the Python wheel and source distribution (uv build)"
                version.set(python.version)
                stagingDirectory.set(layout.buildDirectory.dir("python/staging"))
                outputDirectory.set(layout.buildDirectory.dir("python/dist"))
                mustRunAfter(generate, sync)
                dependsOn(Callable { if (pyproject.get().asFile.exists()) emptyList() else listOf(generate) })
            }
        tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) { it.dependsOn(build) }
        python.publishing.repositories.all { repository ->
            tasks.register<UvPublish>("publishPythonTo${repository.name.capitalized()}") {
                group = PublishingPlugin.PUBLISH_TASK_GROUP
                description = "Publishes Python distributions to ${repository.name} (uv publish)"
                distributions.from(build.flatMap { it.outputDirectory })
                if (!repository.isPyPI) index.set(repository.name)
                token.set(repository.token)
                dryRun.set(python.publishing.dryRun)
            }
        }
        tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME) { publish ->
            publish.dependsOn(python.publishing.defaultRepository.map { "publishPythonTo${it.capitalized()}" })
        }
    }

    private fun Project.configureClean(python: PythonExtension) {
        val directory = layout.projectDirectory
        tasks.named<Delete>(BasePlugin.CLEAN_TASK_NAME) {
            delete(CLEANABLE.map(directory::file))
            delete(
                fileTree(directory) { tree ->
                    tree.include("**/__pycache__/**")
                    tree.exclude(".venv/**", "build/**")
                },
            )
            delete(python.cleanVirtualEnv.map { if (it) listOf(directory.dir(".venv")) else emptyList() })
        }
    }

    private fun org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>.filterExists() = filter {
        it.asFile.exists()
    }

    /** Constants and naming conventions of the plugin. */

    companion object {
        /** The group of Python-specific tasks. */
        const val GROUP = "python"

        private const val BUILD_GROUP = BasePlugin.BUILD_GROUP

        private const val VERIFICATION_GROUP = LifecycleBasePlugin.VERIFICATION_GROUP

        private val CLEANABLE =
            listOf("htmlcov", ".pytest_cache", ".mypy_cache", ".ruff_cache", ".coverage", "coverage.xml", "dist")

        /** The name of the Gradle task running the Poe task [poeName] (e.g. `static-checks` → `poeStaticChecks`). */
        fun poeTaskName(poeName: String): String = "poe" +
            poeName.split('-', '_', '.', ' ', ':').filter { it.isNotEmpty() }.joinToString("") { it.capitalized() }

        private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
    }
}
