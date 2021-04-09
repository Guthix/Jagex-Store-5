plugins { application }

val homePath: String = System.getProperty("user.home")

val revision: Int = 190 // change this to latest revision before running
val includeVersions: Boolean = true // whether to append versions to every group

val logbackVersion: String by rootProject.extra
val progressBarVersion: String by rootProject.extra

dependencies {
    implementation(rootProject)
    implementation(group = "ch.qos.logback", name = "logback-classic", version = logbackVersion)
    implementation(group = "me.tongfei", name = "progressbar", version = progressBarVersion)
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