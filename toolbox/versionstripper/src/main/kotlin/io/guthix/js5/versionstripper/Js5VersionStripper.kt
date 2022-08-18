/*
 * Copyright 2018-2021 Guthix
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.guthix.js5.versionstripper

import io.guthix.js5.Js5ArchiveSettings
import io.guthix.js5.container.Js5Container
import io.guthix.js5.container.Js5Store
import io.guthix.js5.container.disk.Js5DiskStore
import me.tongfei.progressbar.DelegatingProgressBarConsumer
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.Path

private val logger = KotlinLogging.logger {}

object Js5VersionStripper {
    @JvmStatic
    fun main(args: Array<String>) {
        var cacheDir: Path? = null
        for (arg in args) {
            when {
                arg.startsWith("-i=") -> cacheDir = Path(arg.substring(3))
            }
        }
        require(cacheDir != null) { "No cache directory specified to read the cache. Pass -i=DIR as an argument." }
        val store = Js5DiskStore.open(cacheDir)
        val archiveSettings = mutableMapOf<Int, Js5ArchiveSettings>()
        for (archiveId in 0 until store.archiveCount) {
            val archiveSettingsData = store.read(Js5Store.MASTER_INDEX, archiveId)
            archiveSettings[archiveId] = Js5ArchiveSettings.decode(Js5Container.decode(archiveSettingsData))
        }
        val grouupCount = archiveSettings.values.sumOf { it.groupSettings.keys.size }
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
