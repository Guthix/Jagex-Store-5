@file:Suppress("UnstableApiUsage")

rootProject.name = "Jagex-Store-5"

dependencyResolutionManagement {
    versionCatalogs {
        create("deps") {
            version("jdk", "21")
            version("kotlin", "2.1.21")
            version("jagex-bytebuf-extensions", "0.2.0")
            version("logback-classic", "1.5.18")
            version("kotest", "6.0.0.M4")
            version("tongfei-progressbar", "0.10.1")
            version("kotlin-logging", "7.0.7")
            version("tukaani-xz", "1.10")
            version("bouncycastle", "1.80")
            version("apache-compress", "1.21")

            library("jagex-bytebuf-ext", "io.guthix", "jagex-bytebuf-extensions").versionRef("jagex-bytebuf-extensions")
            library("kotlin-logging", "io.github.oshai", "kotlin-logging-jvm").versionRef("kotlin-logging")
            library("tukaani-xz", "org.tukaani", "xz").versionRef("tukaani-xz")
            library("bouncycastle", "org.bouncycastle", "bcprov-jdk18on").versionRef("bouncycastle")
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