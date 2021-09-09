import io.guthix.js5.registerPublication

plugins {
    idea
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
    kotlin("jvm")
}

allprojects {
    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.dokka")

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
        compileKotlin {
            kotlinOptions.jvmTarget = "11"
        }

        compileTestKotlin {
            kotlinOptions.jvmTarget = "11"
        }

        withType<Test> {
            useJUnitPlatform()
        }
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }
}

dependencies {
    implementation(libs.bouncycastle)
    implementation(libs.tukaani.xz)
    implementation(libs.apache.compress)
}

kotlin { explicitApi() }

registerPublication(name = "jagex-store-5", description = "A library for modifying Jagex Store 5 caches")