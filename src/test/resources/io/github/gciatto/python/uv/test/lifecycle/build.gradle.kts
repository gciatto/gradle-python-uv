plugins {
    id("io.github.gciatto.python-uv")
}

group = "io.github.gciatto"
version = "1.2.3-dev04-abc1234"
description = "A demo Python project operated by Gradle"

python {
    pyproject {
        license = "Apache-2.0"
        author("Giovanni Ciatto", "giovanni.ciatto@unibo.it")
        keywords.addAll("demo", "gradle")
        poeTask("hello", "python -c \"print('hello from poe')\"")
        // install the lower bounds of direct dependencies, so that new tool releases cannot break the test
        tool("uv", mapOf("resolution" to "lowest-direct"))
    }
}
