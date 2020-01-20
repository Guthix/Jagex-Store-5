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
@file:Suppress("unused")
package io.guthix.cache.js5.container.heap

import io.guthix.cache.js5.Js5ArchiveSettings
import io.guthix.cache.js5.container.Js5Compression
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.Js5Store
import io.guthix.cache.js5.container.disk.Js5DiskStore
import io.netty.buffer.ByteBuf
import java.io.FileNotFoundException
import java.nio.file.Path

/**
 * A [Js5Store] that holds all data into heap memory.
 */
class Js5HeapStore private constructor(
    private val containerData: MutableMap<Int, MutableMap<Int, ByteBuf>>,
    override var archiveCount: Int
) : Js5Store {
    override fun read(indexId: Int, containerId: Int) = containerData[indexId]?.get(containerId)
        ?: throw FileNotFoundException("Can't read data because index $indexId container $containerId does not exist.")

    override fun write(indexId: Int, containerId: Int, data: ByteBuf) {
        containerData.getOrPut(indexId, { mutableMapOf() })[containerId] = data
    }

    override fun remove(indexId: Int, containerId: Int) {
        containerData.remove(indexId)
    }

    override fun close() { }

    companion object {
        /**
         * Opens a [Js5HeapStore] by reading the data from a [Js5DiskStore].
         *
         * @param appendVersions Whether to append versions to the buffers in the [Js5HeapStore].
         */
        fun open(path: Path, appendVersions: Boolean = false) = Js5DiskStore.open(path).run {
            val data = mutableMapOf<Int, MutableMap<Int, ByteBuf>>()
            val archiveSettings = mutableMapOf<Int, Js5ArchiveSettings>() // used for reading group data

            val archiveSettingsData = data.getOrPut(Js5Store.MASTER_INDEX, { mutableMapOf() })
            for(archiveId in 0 until archiveCount) {
                val rawSettings = read(Js5Store.MASTER_INDEX, archiveId)
                archiveSettingsData[archiveId] = rawSettings
                val settings = Js5ArchiveSettings.decode(Js5Container.decode(rawSettings.duplicate()))
                archiveSettings[archiveId] = settings
            }

            archiveSettings.forEach { (archiveId, archiveSettings) ->
                val archiveData = data.getOrPut(archiveId, { mutableMapOf() })
                archiveSettings.groupSettings.forEach { (groupId, _) ->
                    val rawGroup = read(archiveId, groupId)
                    if(Js5Container.decodeVersion(rawGroup.duplicate()) == null || appendVersions) {
                        archiveData[groupId] = rawGroup
                    } else {
                        archiveData[groupId] = rawGroup.slice(0, rawGroup.readerIndex() - 2)
                    }
                }
            }
            Js5HeapStore(data, archiveSettings.size)
        }
    }
}