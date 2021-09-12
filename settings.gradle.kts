@file:Suppress("UnstableApiUsage")

enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
    val kotlinVersion by extra("1.5.30")
    val dokkaVersion by extra("1.4.32")

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaVersion
    }

    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "jagex-store-5"

include("container")
include("filestore")
include("toolbox")
include("toolbox:downloader")
include("toolbox:versionstripper")