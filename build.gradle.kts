@file:Suppress("UnstableApiUsage")

import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.dokka)
    alias(libs.plugins.gitSemVer)
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.qa)
    alias(libs.plugins.multiJvmTesting)
    alias(libs.plugins.taskTree)
}

/*
 * Project information
 */
group = "io.github.gciatto"
description = "Gradle plugin operating Python projects via uv and Poe the Poet"

class ProjectInfo {
    val longName = "Python via uv for Gradle"
    val website = "https://github.com/gciatto/$name"
    val vcsUrl = "$website.git"
    val pluginId = "$group.python-uv"
    val pluginImplementationClass = "$group.python.uv.PythonUvPlugin"
    val tags = listOf("python", "uv", "poe", "pypi", "multiplatform")
}
val info = ProjectInfo()

gitSemVer {
    buildMetadataSeparator.set("-")
}

repositories {
    mavenCentral()
}

multiJvm {
    jvmVersionForCompilation = oldestJavaSupportedByGradle
    maximumSupportedJvmVersion = latestJavaSupportedByGradle
}

dependencies {
    api(gradleApi())
    api(gradleKotlinDsl())
    implementation(libs.tomlj)
    testImplementation(gradleTestKit())
    testImplementation(libs.apache.commons.lang3)
    testImplementation(libs.konf.yaml)
    testImplementation(libs.classgraph)
    testImplementation(libs.bundles.kotlin.testing)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

inline fun <reified T : Task> Project.disableTrackStateOnWindows() {
    tasks.withType<T>().configureEach {
        doNotTrackState("Windows is a mess and JaCoCo does not work correctly")
    }
}

if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    disableTrackStateOnWindows<Test>()
    disableTrackStateOnWindows<JacocoReport>()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        showCauses = true
        showStackTraces = true
        events(
            *org.gradle.api.tasks.testing.logging.TestLogEvent
                .values(),
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

gradlePlugin {
    plugins {
        website.set(info.website)
        vcsUrl.set(info.vcsUrl)
        create("") {
            id = info.pluginId
            displayName = info.longName
            description = project.description
            implementationClass = info.pluginImplementationClass
            tags.set(info.tags)
        }
    }
}
