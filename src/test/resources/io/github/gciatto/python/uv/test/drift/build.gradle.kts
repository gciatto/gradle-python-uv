plugins {
    id("io.github.gciatto.python-uv")
}

python {
    pyproject {
        description = "Drift detection"
        dependency("packaging>=24.0")
        // pinned here, so that updates of the plugin defaults do not affect this test
        dependencyGroups.set(
            mapOf("dev" to listOf("coverage>=7.14.0", "mypy>=2.1.0", "poethepoet>=0.45.0", "pytest>=9.0.3", "ruff>=0.15.12")),
        )
        if (providers.gradleProperty("addDependency").isPresent) {
            dependency("tomli>=2.0")
        }
    }
}
