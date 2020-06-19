import org.jetbrains.kotlin.util.parseSpaceSeparatedArgs

/**
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
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
    mainClassName = "io.guthix.cache.js5.downloader.Js5Downloader"
}

tasks.run.get().args = listOf(
    "-o=$homePath\\OldScape\\$revision",
    "-a=oldschool1.runescape.com",
    "-r=$revision",
    "-p=43594",
    if(includeVersions) "-v" else ""
)