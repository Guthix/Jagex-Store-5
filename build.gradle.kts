@file:Suppress("ConvertLambdaToReference")

import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    idea
    `maven-publish`
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

group = "io.guthix"
version = "0.3.8"
description = "A library for modifying Jagex Store 5 caches"

val jagexByteBufVersion: String by extra("9efb226d7a")
val kotlinLoggingVersion: String by extra("1.8.3")
val logbackVersion: String by extra("1.2.3")
val xzVersion: String by extra("1.8")
val bouncyCastleVersion: String by extra("1.66")
val apacheCompressVersion: String by extra("1.20")
val progressBarVersion: String by extra("0.8.1")
val kotlinTestVersion: String by extra("4.2.2")
val kotlinVersion: String by extra(project.getKotlinPluginVersion()!!)

allprojects {
    apply(plugin = "kotlin")

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        api(group = "com.github.guthix", name = "jagex-byteBuf", version = jagexByteBufVersion)
        implementation(group = "io.github.microutils", name = "kotlin-logging", version = kotlinLoggingVersion)
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

kotlin { explicitApi() }

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(group = "org.tukaani", name = "xz", version = xzVersion)
    implementation(group = "org.bouncycastle", name = "bcprov-jdk15on", version = bouncyCastleVersion)
    implementation(group = "org.apache.commons", name = "commons-compress", version = apacheCompressVersion)
    testImplementation(group = "ch.qos.logback", name = "logback-classic", version = logbackVersion)
    testImplementation(group = "io.kotest", name = "kotest-runner-junit5-jvm", version = kotlinTestVersion)
    testImplementation(group = "io.kotest", name = "kotest-assertions-core-jvm", version = kotlinTestVersion)
}

publishing {
    repositories {
        maven {
            name = "Github"
            url = uri("https://maven.pkg.github.com/guthix/Jagex-Store-5")
            credentials {
                username = findProject("github.username") as String?
                password = findProperty("github.token") as String?
            }
        }
    }
    publications {
        create<MavenPublication>("Github") {
            from(components["java"])
            pom {
                url.set("https://github.com/guthix/Jagex-Store-5")
                licenses {
                    license {
                        name.set("GNU Lesser General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/guthix/Jagex-Store-5.git")
                    developerConnection.set("scm:git:ssh://github.com/guthix/Jagex-Store-5.git")
                }
            }
        }
    }
}