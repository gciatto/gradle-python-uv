plugins {
    id("io.github.gciatto.python-uv")
}

description = "A project relying on a downloaded uv"

python {
    locked = false // this fixture creates its environment without a committed lockfile
}
