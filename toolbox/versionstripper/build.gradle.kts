import io.guthix.js5.Version

plugins { application }

val homePath: String = System.getProperty("user.home")

val revision: Int = 194

dependencies {
    implementation(rootProject)
    implementation(group = "ch.qos.logback", name = "logback-classic", version = Version.logbackVersion)
    implementation(group = "me.tongfei", name = "progressbar", version = Version.progressBarVersion)
}

application {
    mainClassName = "io.guthix.js5.versionstripper.Js5VersionStripper"
}

tasks.run.get().args = listOf("-i=$homePath\\OldScape\\$revision")