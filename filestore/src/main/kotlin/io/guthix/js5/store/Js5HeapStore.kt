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
@file:Suppress("unused")

package io.guthix.js5.store

import io.guthix.js5.Js5ArchiveSettings
import io.guthix.js5.container.Js5Container
import io.guthix.js5.container.Js5Store
import io.guthix.js5.container.disk.Js5DiskStore
import io.netty.buffer.ByteBuf
import java.io.FileNotFoundException

/**
 * A [Js5Store] that holds all data into heap memory.
 */
public class Js5HeapStore private constructor(
    private val containerData: MutableMap<Int, MutableMap<Int, ByteBuf>>,
    override var archiveCount: Int
) : Js5Store {
    override fun read(indexId: Int, containerId: Int): ByteBuf = containerData[indexId]?.get(containerId)?.duplicate()
        ?: throw FileNotFoundException("Can't read data because index $indexId container $containerId does not exist.")

    override fun write(indexId: Int, containerId: Int, data: ByteBuf) {
        containerData.getOrPut(indexId, ::mutableMapOf)[containerId] = data
    }

    override fun remove(indexId: Int, containerId: Int) {
        containerData.remove(indexId)
    }

    override fun close() {}

    public companion object {
        /**
         * Opens a [Js5HeapStore] by reading the data from a [Js5DiskStore].
         *
         * @param appendVersions Whether to append versions to the buffers in the [Js5HeapStore].
         */
        public fun open(store: Js5DiskStore, appendVersions: Boolean = false): Js5HeapStore {
            val data = mutableMapOf<Int, MutableMap<Int, ByteBuf>>()
            val archiveSettings = mutableMapOf<Int, Js5ArchiveSettings>()

            val archiveSettingsData = data.getOrPut(Js5Store.MASTER_INDEX, ::mutableMapOf)
            for (archiveId in 0 until store.archiveCount) {
                val rawSettings = store.read(Js5Store.MASTER_INDEX, archiveId)
                archiveSettingsData[archiveId] = rawSettings.copy()
                val settings = Js5ArchiveSettings.decode(Js5Container.decode(rawSettings.duplicate()))
                archiveSettings[archiveId] = settings
            }

            archiveSettings.forEach { (archiveId, archiveSettings) ->
                val archiveData = data.getOrPut(archiveId, ::mutableMapOf)
                archiveSettings.groupSettings.forEach { (groupId, _) ->
                    val rawGroup = store.read(archiveId, groupId)
                    if (Js5Container.decodeVersion(rawGroup.duplicate()) == null || appendVersions) {
                        archiveData[groupId] = rawGroup.copy()
                    } else {
                        archiveData[groupId] = rawGroup.slice(0, rawGroup.writerIndex() - 2).copy()
                    }
                }
            }
            return Js5HeapStore(data, archiveSettings.size)
        }
    }
}