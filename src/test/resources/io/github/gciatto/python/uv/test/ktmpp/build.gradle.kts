import io.github.gciatto.kt.mpp.PluginDescriptor
import io.github.gciatto.kt.mpp.Plugins
import io.github.gciatto.kt.mpp.helpers.ProjectType

plugins {
    id("io.github.gciatto.kt-mpp.multi-project-helper") version "6.0.6"
    id("io.github.gciatto.python-uv") apply false
}

group = "io.github.gciatto"
version = "2.0.0-rc.1"

multiProjectHelper {
    defaultProjectType = ProjectType.OTHER
    otherProjects(":py-lib")
    otherProjectTemplate = setOf(Plugins.versions, PluginDescriptor<Any>("io.github.gciatto.python-uv"))
    applyProjectTemplates()
}
