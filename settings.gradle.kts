@file:Suppress("UnstableApiUsage")

enableFeaturePreview("VERSION_CATALOGS")

rootProject.name = "jagex-store-5"

dependencyResolutionManagement {
    versionCatalogs {
        create("deps") {
            version("jdk", "11")
            version("kotlin", "1.5.30")
            version("dokka", "1.5.0")
            version("jagex-bytebuf-extensions", "0.2.0")
            version("logback-classic", "1.2.6")
            version("kotest", "5.0.0.M1")
            version("tongfei-progressbar", "0.9.2")
            version("kotlin-logging", "2.0.11")
            version("tukaani-xz", "1.8")
            version("bouncycastle", "1.69")
            version("apache-compress", "1.21")

            alias("dokka-java").to("org.jetbrains.dokka", "kotlin-as-java-plugin").versionRef("dokka")
            alias("jagex-bytebuf-ext").to("io.guthix", "jagex-bytebuf-extensions")
                .versionRef("jagex-bytebuf-extensions")
            alias("kotlin-logging").to("io.github.microutils", "kotlin-logging-jvm").versionRef("kotlin-logging")
            alias("tukaani-xz").to("org.tukaani", "xz").versionRef("tukaani-xz")
            alias("bouncycastle").to("org.bouncycastle", "bcprov-jdk15on").versionRef("bouncycastle")
            alias("apache-compress").to("org.apache.commons", "commons-compress").versionRef("apache-compress")
            alias("tongfei-progressbar").to("me.tongfei", "progressbar").versionRef("tongfei-progressbar")
            alias("logback-classic").to("ch.qos.logback", "logback-classic").versionRef("logback-classic")
            alias("kotest-junit").to("io.kotest", "kotest-runner-junit5-jvm").versionRef("kotest")
            alias("kotest-assert").to("io.kotest", "kotest-assertions-core-jvm").versionRef("kotest")
            alias("kotest-property").to("io.kotest", "kotest-property").versionRef("kotest")
        }
    }
}

include("container")
include("filestore")
include("toolbox")
include("toolbox:downloader")
include("toolbox:versionstripper")