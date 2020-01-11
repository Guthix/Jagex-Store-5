package io.guthix.cache.js5.container.heap

import io.guthix.cache.js5.Js5ArchiveSettings
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
         */
        fun open(path: Path) = Js5DiskStore.open(path).run {
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
                    archiveData[groupId] = rawGroup
                }
            }
            Js5HeapStore(data, archiveSettings.size)
        }
    }
}