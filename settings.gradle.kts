pluginManagement {
    val kotlinVersion by extra("1.4.0")
    val dokkaVersion by extra("1.4.0-rc")

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaVersion
    }

    repositories {
        gradlePluginPortal()
        jcenter()
    }
}

rootProject.name = "jagex-store-5"

include("toolbox")
include("toolbox:downloader")
include("toolbox:versionstripper")