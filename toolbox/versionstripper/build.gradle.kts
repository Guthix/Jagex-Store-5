plugins { application }

val homePath: String = System.getProperty("user.home")

val revision: Int = 195

dependencies {
    implementation(rootProject)
    implementation(libs.logback)
    implementation(libs.tongfei.progressbar)
}

application {
    mainClass.set("io.guthix.js5.versionstripper.Js5VersionStripper")
}

tasks.run.get().args = listOf("-i=$homePath\\OldScape\\$revision")