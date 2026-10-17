# Agent Instructions

## Project Scope

A Gradle plugin (`io.github.gciatto.python-uv`) operating Python projects via uv and Poe the Poet.
User-facing documentation lives in `README.md`; do not duplicate it here.

- Build logic: `build.gradle.kts`; project identity and settings plugins: `settings.gradle.kts`.
- Plugin entry point: `src/main/kotlin/io/github/gciatto/python/uv/PythonUvPlugin.kt`.
- DSL: `PythonExtension.kt`, `PyprojectSpec.kt`, `LifecycleMapping.kt`, `PythonPublishingSpec.kt`;
  defaults in `Conventions.kt` (they mirror gciatto/template-python-project-uv).
- Tasks: `src/main/kotlin/io/github/gciatto/python/uv/tasks/`.
- uv lookup and download: `src/main/kotlin/io/github/gciatto/python/uv/provisioning/` (`UvResolver` is the entry point).
- `pyproject.toml` merging is done by `src/main/resources/io/github/gciatto/python/uv/pyproject_sync.py`,
  run via `uv run --no-project --with tomlkit --with packaging`.
- Functional tests: one Gradle project per directory under `src/test/resources/**`, described by `test.yaml`.

## Working Rules

- Keep tasks configuration-cache compatible: no `Project` access at execution time, use `ExecOperations`
  and `FileSystemOperations`.
- Collection properties with defaults are initialised via `set` (not `convention`), so that `add`/`put` extend them.
- Keep `pyproject_sync.py` compatible with Python 3.10, formatted and linted with ruff (line length 100),
  and type-checked with mypy.
- Do not hardcode dependency or plugin versions in build logic when a version catalog entry exists.
- Commits follow Conventional Commits.

## Validation

- `./gradlew build` (TestKit tests require network access to PyPI; uv is downloaded if missing).

## Version Sources

- `gradle/libs.versions.toml` for the version catalog and aliased plugins.
- `settings.gradle.kts` for settings-level plugin declarations.
- `gradle/wrapper/gradle-wrapper.properties` for the Gradle wrapper distribution.
- `Conventions.kt` for the default Python tool versions and the pinned uv version (updated by Renovate via regex managers).
