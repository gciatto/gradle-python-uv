# gradle-python-uv

A Gradle plugin operating Python projects via [uv](https://docs.astral.sh/uv/) and
[Poe the Poet](https://poethepoet.natn.io/), following the conventions of
[template-python-project-uv](https://github.com/gciatto/template-python-project-uv).

Python projects become first-class citizens of Gradle builds:
Gradle's lifecycle tasks (`classes`, `test`, `check`, `assemble`, `build`, `run`, `publish`, `clean`)
are mapped onto uv commands and Poe tasks, so that Python sub-projects can live alongside
JVM/JS/multi-platform ones, e.g. in [kt-mpp](https://github.com/gciatto/kt-mpp) builds.

## Requirements

- Gradle 9+, Java 17+.
- Nothing else: an installed [uv](https://docs.astral.sh/uv/) is used if found, otherwise the plugin
  downloads a pinned one (see [uv provisioning](#uv-provisioning)). uv provisions Python itself.

## Quick start

```kotlin
// build.gradle.kts
plugins {
    id("io.github.gciatto.python-uv") version "<version>"
}

group = "io.github.username"
version = "1.0.0"
description = "My Python project"

python {
    pyproject {
        license = "Apache-2.0"
        author("Jane Doe", "jane@example.com")
        dependency("requests>=2.32")
        devDependency("hypothesis>=6")
        poeTask("docs", "mkdocs build")
    }
}
```

```bash
./gradlew generatePyproject   # creates/updates pyproject.toml: commit it!
./gradlew build               # uv sync, static checks, tests, wheel + sdist
./gradlew run --args="Hello"  # uv run <script> Hello
```

With no configuration at all, `generatePyproject` produces the `pyproject.toml` of
`template-python-project-uv`: `uv_build` backend, a `dev` dependency group (coverage, mypy,
poethepoet, pytest, ruff), the same Poe tasks, Ruff and pytest settings, and a TestPyPI index.

The package is expected at `<moduleRoot>/<moduleName>/`, where `moduleName` defaults to the
project name with non-alphanumeric characters replaced by `_`, and `moduleRoot` defaults to the
project directory (flat layout).

## `pyproject.toml`: who owns what

`pyproject.toml` is generated from Gradle, but it is a tracked source file:
it stays usable by plain `uv` commands, and by Renovate for dependency updates.
`generatePyproject` therefore *merges* the Gradle configuration into the file
(via [tomlkit](https://github.com/python-poetry/tomlkit), in an ephemeral uv environment):

- keys Gradle does not manage are left untouched, comments and formatting included;
- unchanged values are not rewritten;
- in dependency arrays, Gradle decides *which* packages are listed, while the version specifier
  with the **highest lower bound** wins: Renovate bumps in `pyproject.toml` survive regeneration,
  and bumps in Gradle take effect;
- Poe tasks, tool tables, and `[[tool.uv.index]]` entries are merged by name, so hand-written
  ones are kept.

`checkPyproject` (part of `check`) fails, printing a diff, whenever the file is out of sync with
the Gradle configuration: run `./gradlew generatePyproject` and commit the result.

The project version is **not** written into `pyproject.toml` (unless `python.writeVersion = true`),
as it may change at every commit (e.g. with
[git-sensitive-semantic-versioning](https://github.com/DanySK/git-sensitive-semantic-versioning-gradle-plugin)).
Instead, `uvBuild` stages the sources in `build/python/staging`, sets the version there
(`uv version --frozen`), and builds from there. Gradle versions are converted into
[PEP 440](https://peps.python.org/pep-0440/) ones, e.g. `1.2.4-dev05-abc1234` → `1.2.4.dev5`,
`2.0.0-rc.1` → `2.0.0rc1` (local labels are dropped, as PyPI rejects them).

## uv provisioning

The uv executable is resolved once per build, at execution time, as follows:

1. `python.uvExecutable`, if set (also via the `python.uvExecutable` Gradle property or the `UV_EXECUTABLE`
   environment variable): either a path (absolute, or relative to the project directory),
   or a command name, looked up as in step 2;
2. unless `python.uvProvisioning = ALWAYS`, an installed uv, looked up in `PATH` (case-insensitively, with
   `uv.exe` on Windows) and then in the directories where installers and package managers put it, which may
   be missing from the `PATH` of IDEs launched from the desktop: `$UV_INSTALL_DIR`, `$XDG_BIN_HOME`,
   `~/.local/bin`, `$CARGO_HOME/bin`, `~/.cargo/bin`, WinGet/Scoop/Chocolatey directories on Windows,
   Homebrew/MacPorts directories on macOS, `/usr/local/bin`, `/usr/bin`, `/snap/bin` on Linux;
3. unless `python.uvProvisioning = NEVER`, the pinned uv version (`python.uvVersion`), downloaded once into
   `~/.gradle/caches/python-uv/uv/<version>/<platform>/` and shared by all projects.

uv is downloaded as the platform-specific wheel it publishes on PyPI (Linux glibc/musl, macOS, and Windows,
on x86_64, aarch64, and other architectures), via the standard "simple" index API, so PyPI mirrors work too.
The wheel is verified against the SHA-256 published by the index. Downloads honour the JVM proxy settings
(e.g. `systemProp.https.proxyHost` in `gradle.properties`).

| Setting | Gradle property | Default |
|---|---|---|
| `uvExecutable` | `python.uvExecutable` | `UV_EXECUTABLE`, or unset |
| `uvProvisioning` (`AUTO`, `ALWAYS`, `NEVER`) | `python.uvProvisioning` | `AUTO` |
| `uvVersion` | `python.uvVersion` | pinned, updated by Renovate |
| `uvIndexUrl` | `python.uvIndexUrl` | `UV_DEFAULT_INDEX`, `UV_INDEX_URL`, `PIP_INDEX_URL`, or `https://pypi.org/simple` |
| `uvCacheDirectory` | | `<Gradle user home>/caches/python-uv/uv` |

`./gradlew uvInstall` resolves (and possibly downloads) uv, printing its location and version.
Use `python.uvProvisioning = ALWAYS` for reproducible builds (everyone uses the same uv version),
and `NEVER` on offline or locked-down machines.

## Tasks

| Gradle task | What it does |
|---|---|
| `generatePyproject` | Merges the Gradle configuration into `pyproject.toml` (run it manually, commit the result) |
| `checkPyproject` | Fails if `pyproject.toml` is out of sync with the Gradle configuration |
| `uvInstall` | Resolves uv (downloading it if needed), printing its location and version |
| `uvPythonInstall` | `uv python install <pythonVersion> <testedPythonVersions...>` |
| `uvLock` | `uv lock` |
| `uvSync` | `uv sync` (`--locked` on CI); generates `pyproject.toml` first if missing |
| `poe<Name>` | `uv run poe <name>`, one per Poe task (from `pyproject.toml` and the Gradle configuration) |
| `classes`, `testClasses` | `poeCompile` |
| `test` | `poeTest` |
| `check` | `test`, `poeStaticChecks`, `checkPyproject` |
| `format` | `poeStaticFixes` |
| `coverage` | `poeCoverage`, then `poeCoverageReport` |
| `testPython<X_Y>`, `testAllPythonVersions` | `poe test` with Python X.Y, each in its own virtual environment under `build/python/venvs` |
| `uvBuild` (`assemble`, `build`) | `uv build` into `build/python/dist` |
| `run` | `uv run <script>` (first `[project.scripts]` entry), or `uv run python -m <module>`; supports `--args` |
| `publishPythonTo<Repo>`, `publish` | `uv publish` (to `pypi` by default) |
| `clean` | Deletes Python caches, coverage reports, `dist` (and `.venv` if `python.cleanVirtualEnv = true`) |

Poe tasks accept extra arguments via `--args`, e.g. `./gradlew poeTest --args="-k greet -x"`.
If `classes`, `test`, `run`, ... already exist (e.g. because the `java` plugin is applied *before*
this one), they are made to depend on the Python tasks instead.

The mapping between lifecycle tasks and Poe tasks is configurable:

```kotlin
python {
    lifecycle {
        test = listOf("coverage")                     // test runs `poe coverage`
        check = listOf("static-checks", "audit")      // check runs `poe static-checks`, then `poe audit`
    }
}
```

## Configuration reference

```kotlin
python {
    uvExecutable = "/opt/uv/bin/uv"                  // default: unset (see "uv provisioning")
    uvProvisioning = UvProvisioning.ALWAYS           // import io.github.gciatto.python.uv.UvProvisioning
    uvVersion = "0.13.1"
    pythonVersion = "3.12"                           // default: content of .python-version
    testedPythonVersions("3.10", "3.11", "3.12", "3.13")
    pyprojectFile = layout.projectDirectory.file("pyproject.toml")
    version = "1.2.3"                                // default: PEP 440 conversion of project.version
    writeVersion = false
    locked = true                                    // default: true iff CI=true
    cleanVirtualEnv = false
    runScript = "my-cli"                             // default: first [project.scripts] entry

    pyproject {
        name = "my-project"                          // default: project.name
        description = "..."                          // default: project.description
        readme = "README.md"                         // default: README.md, if it exists
        license = "MIT"
        requiresPython = ">=3.10"
        keywords.addAll("a", "b")
        classifiers.add("Programming Language :: Python :: 3")
        urls.put("Homepage", "https://example.com")
        author("Jane Doe", "jane@example.com")
        dependency("numpy>=2")
        optionalDependency("plot", "matplotlib>=3.9")
        dependencyGroup("docs", "mkdocs>=1.6")
        devDependency("hypothesis>=6")
        script("my-cli", "my_project.__main__:main")  // default: <module> = "<module>.__main__:main", if __main__.py exists
        moduleName = "my_project"
        moduleRoot = "src"                           // for src layouts; default: "" (flat layout)
        buildBackend = "uv_build"
        buildSystemRequires.set(listOf("uv_build>=0.13.0,<0.14.0"))
        poeTask("docs", "mkdocs build")
        poeTask("serve", mapOf("shell" to "mkdocs serve", "help" to "Serve the docs"))
        poeSequence("all", "static-checks", "test")
        tool("ruff.lint", mapOf("select" to listOf("E", "F", "I")))
    }

    publishing {
        defaultRepository = "pypi"                   // target of `publish`
        dryRun = false                               // default: -Ppython.publish.dryRun
        repository("internal") {
            url = "https://pypi.example.com/simple/"
            publishUrl = "https://pypi.example.com/legacy/"
            token = providers.environmentVariable("INTERNAL_TOKEN")
        }
    }
}
```

Collections are pre-filled with the defaults: `add`/`put` (and DSL methods such as `devDependency`)
extend them, while `set` replaces them (e.g. `pyproject.poeTasks.set(mapOf(...))`).

Publishing tokens default to the `<NAME>_TOKEN` environment variable (`PYPI_TOKEN`,
`TESTPYPI_TOKEN` or `TEST_PYPI_TOKEN`), or to the `<name>Token` Gradle property (e.g. `-PpypiToken=...`).
They are passed to uv via `UV_PUBLISH_TOKEN`, never on the command line.
Without a token, uv falls back to its other credential sources, e.g. trusted publishing on GitHub Actions.

## Multi-platform builds with kt-mpp

kt-mpp applies plugins by id, so Python sub-projects can be declared as "other" projects:

```kotlin
// root build.gradle.kts
import io.github.gciatto.kt.mpp.PluginDescriptor
import io.github.gciatto.kt.mpp.Plugins
import io.github.gciatto.kt.mpp.helpers.ProjectType

plugins {
    id("io.github.gciatto.kt-mpp.multi-project-helper") version "<kt-mpp version>"
    id("io.github.gciatto.python-uv") version "<version>" apply false
}

group = "io.github.username"
version = "1.0.0"

multiProjectHelper {
    defaultProjectType = ProjectType.KOTLIN
    jvmProjects(":core-jvm")
    otherProjects(":core-python")
    otherProjectTemplate = setOf(Plugins.versions, PluginDescriptor<Any>("io.github.gciatto.python-uv"))
    applyProjectTemplates()
}
```

`./gradlew check build publish` then covers Kotlin and Python sub-projects alike, and the
`versions` plugin propagates the root version to Python distributions.
Since the plugin is applied by the root project, configure Python sub-projects without
generated accessors:

```kotlin
// core-python/build.gradle.kts
import io.github.gciatto.python.uv.PythonExtension

configure<PythonExtension> {
    pyproject { dependency("numpy>=2") }
}
```

## Renovate

`pyproject.toml` and `uv.lock` are tracked, so Renovate's `pep621` manager updates them as usual.
Thanks to the merge semantics above, Renovate's bumps are preserved by `generatePyproject` and
accepted by `checkPyproject`, as long as they raise the lower bounds declared in Gradle.
Bumping a version in Gradle (or adding/removing dependencies there) requires regenerating the file.

## Development

```bash
./gradlew build     # compiles, runs ktlint/detekt, and tests (TestKit tests require network access to PyPI)
```

Functional tests live in `src/test/resources/**/test.yaml` (one Gradle project per directory).
The bundled merge script is `src/main/resources/io/github/gciatto/python/uv/pyproject_sync.py`.
