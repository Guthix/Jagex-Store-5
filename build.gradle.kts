@file:Suppress("ConvertLambdaToReference")

import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    `maven-publish`
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

group = "io.guthix"
version = "0.3.8"
description = "A library for modifying Jagex Store 5 caches"

val jagexByteBufVersion: String by extra("77cc6fd2a3")
val kotlinLoggingVersion: String by extra("1.7.10")
val licensePluginVersion: String by extra("0.15.0")
val logbackVersion: String by extra("1.2.3")
val xzVersion: String by extra("1.8")
val bouncyCastleVersion: String by extra("1.65.01")
val apacheCompressVersion: String by extra("1.20")
val progressBarVersion: String by extra("0.8.1")
val kotlinTestVersion: String by extra("4.1.0.RC2")
val kotlinVersion: String by extra(project.getKotlinPluginVersion()!!)

allprojects {
    apply(plugin = "kotlin")

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
    implementation(kotlin("stdlib-jdk8"))
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
                        name.set("APACHE LICENSE, VERSION 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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

repositories {
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    mavenCentral()
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks

compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}