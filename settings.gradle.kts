@file:Suppress("UnstableApiUsage")

enableFeaturePreview("VERSION_CATALOGS")

rootProject.name = "jagex-store-5"

dependencyResolutionManagement {
    versionCatalogs {
        create("deps") {
            version("jdk", "17")
            version("kotlin", "1.7.10")
            version("dokka", "1.7.10")
            version("jagex-bytebuf-extensions", "0.2.0")
            version("logback-classic", "1.2.6")
            version("kotest", "5.0.0.M1")
            version("tongfei-progressbar", "0.9.2")
            version("kotlin-logging", "2.0.11")
            version("tukaani-xz", "1.8")
            version("bouncycastle", "1.69")
            version("apache-compress", "1.21")

            library("dokka-java", "org.jetbrains.dokka", "kotlin-as-java-plugin").versionRef("dokka")
            library("jagex-bytebuf-ext", "io.guthix", "jagex-bytebuf-extensions").versionRef("jagex-bytebuf-extensions")
            library("kotlin-logging", "io.github.microutils", "kotlin-logging-jvm").versionRef("kotlin-logging")
            library("tukaani-xz", "org.tukaani", "xz").versionRef("tukaani-xz")
            library("bouncycastle", "org.bouncycastle", "bcprov-jdk15on").versionRef("bouncycastle")
            library("apache-compress", "org.apache.commons", "commons-compress").versionRef("apache-compress")
            library("tongfei-progressbar", "me.tongfei", "progressbar").versionRef("tongfei-progressbar")
            library("logback-classic", "ch.qos.logback", "logback-classic").versionRef("logback-classic")
            library("kotest-junit", "io.kotest", "kotest-runner-junit5-jvm").versionRef("kotest")
            library("kotest-assert", "io.kotest", "kotest-assertions-core-jvm").versionRef("kotest")
            library("kotest-property", "io.kotest", "kotest-property").versionRef("kotest")
        }
    }
}

include("container")
include("filestore")
include("toolbox")
include("toolbox:downloader")
include("toolbox:versionstripper")