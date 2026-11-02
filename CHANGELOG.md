## 1.0.0 (2026-11-02)

### Features

* add the python DSL, reproducing the conventions of template-python-project-uv ([1720f82](https://github.com/gciatto/gradle-python-uv/commit/1720f82f0f824a1297afd22c48d139814a18f291))
* convert Gradle versions into PEP 440 versions ([67eb8b4](https://github.com/gciatto/gradle-python-uv/commit/67eb8b43de05cf411289aee36305b4a245df79ed))
* look up uv beyond PATH and download it from PyPI when missing ([8df40fd](https://github.com/gciatto/gradle-python-uv/commit/8df40fdb3359b3e021721a496348931b4c71cba7))
* map Gradle lifecycle tasks onto uv commands and Poe tasks ([88df75c](https://github.com/gciatto/gradle-python-uv/commit/88df75c291941c963e67feb2b74789ccb340d5a5))
* merge the Gradle configuration into pyproject.toml preserving manual and Renovate edits ([fd3d456](https://github.com/gciatto/gradle-python-uv/commit/fd3d4568dbd3ca0536f99b06fbde9a6970fc04b4))

### Bug Fixes

* **ci:** grant release permissions to the reusable workflow ([1e56609](https://github.com/gciatto/gradle-python-uv/commit/1e56609a7d9361dc1a0dc0f88acb311787f04325))

### Documentation

* document the plugin ([8bf883b](https://github.com/gciatto/gradle-python-uv/commit/8bf883b2023e70fc1e89bb304d0b7cb995250a07))
* document uv provisioning ([ba8447b](https://github.com/gciatto/gradle-python-uv/commit/ba8447b0dd9b9afca11a553cb8d36b61937154d0))

### Tests

* check the provisioned uv version without hardcoding it ([40b3e45](https://github.com/gciatto/gradle-python-uv/commit/40b3e4545dc6c7c1d73fed234b6333d7a5ea37be))
* cover generation, drift detection, lifecycle, and kt-mpp integration ([f239006](https://github.com/gciatto/gradle-python-uv/commit/f239006b7a009d7a938c3446fb22e7d10769a66f))
* cover uv lookup and provisioning ([b1f4f8b](https://github.com/gciatto/gradle-python-uv/commit/b1f4f8bca409a926a07b85734cc3bc126d5afff2))
* disable locked sync for fixtures without lockfiles ([26c6a6b](https://github.com/gciatto/gradle-python-uv/commit/26c6a6b129427c2c33ac9ad9c81dc1b647b825f8))
* install the lower bounds of fixture dependencies, so that new tool releases cannot break tests ([6283726](https://github.com/gciatto/gradle-python-uv/commit/6283726ae85f60353219331515d63e4aeaba4aee))
* use host platform for uv locator filesystem checks ([23b46fa](https://github.com/gciatto/gradle-python-uv/commit/23b46faff4da84bc5265e516ba4222ffb0051cb8))

### Build and continuous integration

* configure the project as the io.github.gciatto.python-uv plugin ([dc08bcc](https://github.com/gciatto/gradle-python-uv/commit/dc08bcc665628dfad50666a24f1bd219799f6f15))
* install uv before building ([e341228](https://github.com/gciatto/gradle-python-uv/commit/e341228e452d043a76f79e68bd5b680e0cf69e1b))
* let the plugin provision uv on Windows and macOS ([73887fa](https://github.com/gciatto/gradle-python-uv/commit/73887facd71ad54043cad9e6407cc2b5e57bd98c))
* publish only to the Gradle Plugin Portal ([9e2b794](https://github.com/gciatto/gradle-python-uv/commit/9e2b794bedcc4fc54b3d7bfdc598779f5dd1763b))
* **renovate:** do not update the Python dependencies of test fixtures ([df77766](https://github.com/gciatto/gradle-python-uv/commit/df77766a19cbb2617fb56009156d21535722c8a1))
* **renovate:** update the default Python tool versions of the plugin ([7d0eec8](https://github.com/gciatto/gradle-python-uv/commit/7d0eec8dcd6f924f0536820c2511e76a707e3e52))
* **renovate:** update the uv version pinned by the plugin ([e0cd5a9](https://github.com/gciatto/gradle-python-uv/commit/e0cd5a9b05057df772c57cced8cb9b9657a82330))

### General maintenance

* **gitignore:** ignore .env and bin/ ([94dd1c8](https://github.com/gciatto/gradle-python-uv/commit/94dd1c83239676d75c7d2fc715e54d512e793fb0))
* initialize from DanySK/template-for-gradle-plugins ([976e77b](https://github.com/gciatto/gradle-python-uv/commit/976e77b40e87eb353362a1ce47c1b8b7fc3e7462))
