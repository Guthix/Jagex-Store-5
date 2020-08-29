plugins { application }

val homePath: String = System.getProperty("user.home")

val revision: Int = 190
val logbackVersion: String by rootProject.extra
val progressBarVersion: String by rootProject.extra

dependencies {
    implementation(rootProject)
    implementation(group = "ch.qos.logback", name = "logback-classic", version = logbackVersion)
    implementation(group = "me.tongfei", name = "progressbar", version = progressBarVersion)
}

application {
    mainClassName = "io.guthix.cache.js5.versionstripper.Js5VersionStripper"
}

tasks.run.get().args = listOf("-i=$homePath\\OldScape\\$revision")