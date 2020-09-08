@file:Suppress("ConvertLambdaToReference")

import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import java.net.URI

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

val repoUrl: String = "https://github.com/guthix/Jagex-Store-5"
val gitSuffix: String = "github.com/guthix/Jagex-Store-5.git"

val jagexByteBufVersion: String by extra("0.1")
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
    apply(plugin = "org.jetbrains.dokka")

    repositories {
        mavenCentral()
        jcenter()
    }

    dependencies {
        api(group = "io.guthix", name = "jagex-bytebuf", version = jagexByteBufVersion)
        implementation(group = "io.github.microutils", name = "kotlin-logging", version = kotlinLoggingVersion)
        dokkaHtmlPlugin(group = "org.jetbrains.dokka", name = "kotlin-as-java-plugin", version = kotlinVersion)
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
    implementation(group = "org.tukaani", name = "xz", version = xzVersion)
    implementation(group = "org.bouncycastle", name = "bcprov-jdk15on", version = bouncyCastleVersion)
    implementation(group = "org.apache.commons", name = "commons-compress", version = apacheCompressVersion)
    testImplementation(group = "ch.qos.logback", name = "logback-classic", version = logbackVersion)
    testImplementation(group = "io.kotest", name = "kotest-runner-junit5-jvm", version = kotlinTestVersion)
    testImplementation(group = "io.kotest", name = "kotest-assertions-core-jvm", version = kotlinTestVersion)
}

kotlin { explicitApi() }

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    repositories {
        maven {
            name = "MavenCentral"
            url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
        maven {
            name = "GitHubPackages"
            url = URI("https://maven.pkg.github.com/guthix/Jagex-Store-5")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
            pom {
                name.set("Jagex Store 5")
                description.set(rootProject.description)
                url.set(repoUrl)
                licenses {
                    license {
                        name.set("APACHE LICENSE, VERSION 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set("scm:git:git://$gitSuffix")
                    developerConnection.set("scm:git:ssh://$gitSuffix")
                    url.set(repoUrl
                    )
                }
                developers {
                    developer {
                        id.set("bart")
                        name.set("Bart van Helvert")
                    }
                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(System.getenv("SIGNING_KEY"), System.getenv("SIGNING_PASSWORD"))
    sign(publishing.publications["default"])
}