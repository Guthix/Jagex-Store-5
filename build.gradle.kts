plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
    kotlin("jvm")
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
        api(rootProject.libs.jagex.bytebuf)
        implementation(rootProject.libs.kotlin.logging)
        dokkaHtmlPlugin(rootProject.libs.dokka)
        testImplementation(rootProject.libs.logback)
        testImplementation(rootProject.libs.kotest.junit)
        testImplementation(rootProject.libs.kotest.assert)
    }

    tasks {
        withType<Test> {
            useJUnitPlatform()
        }
    }

    kotlin {
        jvmToolchain {
            (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of("11"))
        }
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }
}