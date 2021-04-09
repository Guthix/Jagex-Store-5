import io.guthix.js5.Version

plugins { application }

val homePath: String = System.getProperty("user.home")

val revision: Int = 194 // change this to latest revision before running
val includeVersions: Boolean = true // whether to append versions to every group

dependencies {
    implementation(rootProject)
    implementation(group = "ch.qos.logback", name = "logback-classic", version = Version.logbackVersion)
    implementation(group = "me.tongfei", name = "progressbar", version = Version.progressBarVersion)
}

application {
    mainClassName = "io.guthix.js5.downloader.Js5Downloader"
}

tasks.run.get().args = listOf(
    "-o=$homePath\\OldScape\\$revision",
    "-a=oldschool1.runescape.com",
    "-r=$revision",
    "-p=43594",
    if (includeVersions) "-v" else ""
)