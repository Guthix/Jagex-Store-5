@file:Suppress("ConvertLambdaToReference", "UnstableApiUsage")

import io.guthix.js5.registerPublication

plugins {
    idea
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
    kotlin("jvm")
}

group = "io.guthix"
version = "0.4.0"
description = "A library for modifying Jagex Store 5 caches"

allprojects {
    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.dokka")

    repositories {
        mavenCentral()
    }

    dependencies {
        api(rootProject.libs.jagex.bytebuf)
        implementation(rootProject.libs.kotlin.logging)
        dokkaHtmlPlugin(rootProject.libs.dokka)
    }

    tasks {
        compileKotlin {
            kotlinOptions.jvmTarget = "11"
        }

        compileTestKotlin {
            kotlinOptions.jvmTarget = "11"
        }
    }
}

dependencies {
    implementation(libs.bouncycastle)
    implementation(libs.tukaani.xz)
    implementation(libs.apache.compress)
    testImplementation(libs.logback)
    testImplementation(libs.kotest.junit)
    testImplementation(libs.kotest.assert)
}

kotlin { explicitApi() }

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    withJavadocJar()
    withSourcesJar()
}

registerPublication(
    publicationName = "jagexStore5",
    pomName = "jagex-store-5"
)