/**
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5.versionstripper

import io.guthix.cache.js5.Js5ArchiveSettings
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.Js5Store
import io.guthix.cache.js5.container.disk.Js5DiskStore
import me.tongfei.progressbar.DelegatingProgressBarConsumer
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import mu.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

object Js5VersionStripper {
    @JvmStatic
    fun main(args: Array<String>) {
        var cacheDir: Path? = null
        for (arg in args) {
            when {
                arg.startsWith("-i=") -> cacheDir = Path.of(arg.substring(3))
            }
        }
        require(cacheDir != null) { "No cache directory specified to read the cache. Pass -i=DIR as an argument." }
        val store = Js5DiskStore.open(cacheDir)
        val archiveSettings = mutableMapOf<Int, Js5ArchiveSettings>()
        for (archiveId in 0 until store.archiveCount) {
            val archiveSettingsData = store.read(Js5Store.MASTER_INDEX, archiveId)
            archiveSettings[archiveId] = Js5ArchiveSettings.decode(Js5Container.decode(archiveSettingsData))
        }
        val grouupCount = archiveSettings.values.sumBy { it.groupSettings.keys.size }
        val progressBar = ProgressBarBuilder()
            .setInitialMax(grouupCount.toLong())
            .setTaskName("Remover")
            .setStyle(ProgressBarStyle.ASCII)
            .setConsumer(DelegatingProgressBarConsumer(logger::info))
            .build()
        progressBar.use { pb ->
            archiveSettings.forEach { (archiveId, archiveSettings) ->
                pb.extraMessage = "Removing from archive $archiveId"
                archiveSettings.groupSettings.forEach { (groupId, _) ->
                    val data = store.read(archiveId, groupId)
                    Js5Container.decodeVersion(data.duplicate())?.let {
                        store.write(archiveId, groupId, data.slice(0, data.readableBytes() - Short.SIZE_BYTES))
                    }
                    pb.step()
                }
            }
        }
    }
}
