plugins {
    kotlin("jvm") version "1.7.10"
    id("org.jetbrains.dokka") version "1.7.10"
    `maven-publish`
    signing
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    group = "io.guthix"

    repositories {
        mavenCentral()
    }

    dependencies {
        api(rootProject.deps.jagex.bytebuf.ext)
        implementation(rootProject.deps.kotlin.logging)
        dokkaHtmlPlugin(rootProject.deps.dokka.java)
        testImplementation(rootProject.deps.logback.classic)
        testImplementation(rootProject.deps.kotest.junit)
        testImplementation(rootProject.deps.kotest.assert)
        testImplementation(rootProject.deps.kotest.property)
    }

    tasks {
        withType<Test> {
            useJUnitPlatform()
        }
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(rootProject.deps.versions.jdk.get()))
        }
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }
}