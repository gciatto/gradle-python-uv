import io.github.gciatto.python.uv.PythonExtension

// the plugin is applied by kt-mpp from the root project, hence no generated accessors
configure<PythonExtension> {
    locked = false // this fixture creates its environment without a committed lockfile
    pyproject {
        // install the lower bounds of direct dependencies, so that new tool releases cannot break the test
        tool("uv", mapOf("resolution" to "lowest-direct"))
    }
}
