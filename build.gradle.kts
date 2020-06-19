@file:Suppress("ConvertLambdaToReference")

import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    idea
    `maven-publish`
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.github.hierynomus.license")
}

group = "io.guthix"
version = "0.3.8"
description = "A library for modifying Jagex Store 5 caches"

val licenseHeader: File by extra(file("gradle/LICENSE_HEADER"))

val jagexByteBufVersion: String by extra("8fa3fe2cbe")
val kotlinLoggingVersion: String by extra("1.7.8")
val licensePluginVersion: String by extra("0.15.0")
val logbackVersion: String by extra("1.2.3")
val xzVersion: String by extra("1.8")
val bouncyCastleVersion: String by extra("1.64")
val apacheCompressVersion: String by extra("1.19")
val progressBarVersion: String by extra("0.8.0")
val kotlinTestVersion: String by extra("3.4.2")
val kotlinVersion: String by extra(project.getKotlinPluginVersion()!!)

allprojects {
    apply(plugin = "kotlin")
    apply(plugin = "com.github.hierynomus.license")

    repositories {
        mavenCentral()
        jcenter()
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://jitpack.io")
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation(group = "com.github.guthix", name = "Jagex-ByteBuf", version = jagexByteBufVersion)
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

    license {
        header = licenseHeader
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
    testImplementation(group = "io.kotlintest", name = "kotlintest-runner-junit5", version = kotlinTestVersion)
}

tasks.dokka {
    outputFormat = "html"
    outputDirectory = "$buildDir/javadoc"
}

val dokkaJar: Jar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    from(tasks.dokka)
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
            artifact(dokkaJar)
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