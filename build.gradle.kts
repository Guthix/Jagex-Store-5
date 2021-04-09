@file:Suppress("ConvertLambdaToReference")

import io.guthix.js5.registerPublication
import io.guthix.js5.Version

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
        jcenter()
    }

    dependencies {
        api(group = "io.guthix", name = "jagex-bytebuf", version = Version.jagexByteBufVersion)
        implementation(group = "io.github.microutils", name = "kotlin-logging-jvm", version = Version.kLoggingVersion)
        dokkaHtmlPlugin(group = "org.jetbrains.dokka", name = "kotlin-as-java-plugin", version = Version.kotlinVersion)
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
    implementation(group = "org.tukaani", name = "xz", version = Version.xzVersion)
    implementation(group = "org.bouncycastle", name = "bcprov-jdk15on", version = Version.bouncyCastleVersion)
    implementation(group = "org.apache.commons", name = "commons-compress", version = Version.apacheCompressVersion)
    testImplementation(group = "ch.qos.logback", name = "logback-classic", version = Version.logbackVersion)
    testImplementation(group = "io.kotest", name = "kotest-runner-junit5-jvm", version = Version.koTestVersion)
    testImplementation(group = "io.kotest", name = "kotest-assertions-core-jvm", version = Version.koTestVersion)
}

kotlin { explicitApi() }

tasks.withType<Test> {
    useJUnitPlatform()
}

registerPublication(
    publicationName = "jagexStore5",
    pomName = "jagex-store-5"
)