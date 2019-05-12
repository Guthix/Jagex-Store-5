/*
 * Copyright (C) 2019 Guthix
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.js5

import java.io.File
import io.guthix.cache.js5.store.FileSystem
import io.guthix.cache.js5.util.*
import mu.KotlinLogging
import java.io.IOException
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

@ExperimentalUnsignedTypes
open class Js5Cache(directory: File, val settingsXtea: MutableMap<Int, IntArray> = mutableMapOf()) : AutoCloseable {
    private val fileStore = FileSystem(directory)

    @ExperimentalUnsignedTypes
    protected val archiveSettings = MutableList(fileStore.archiveCount) {
        Js5ArchiveSettings.decode(
            Js5Container.decode(
                fileStore.read(
                    FileSystem.MASTER_INDEX,
                    it
                ),
                settingsXtea[it] ?: XTEA_ZERO_KEY
            )
        )
    }

    init {
        logger.info("Loaded cache with ${archiveSettings.size} archives")
    }

    @ExperimentalUnsignedTypes
    public fun archiveIds(archiveId: Int) = getArchiveSettings(archiveId).js5GroupSettings.keys

    @ExperimentalUnsignedTypes
    public fun fileIds(archiveId: Int, groupId: Int) =
        getArchiveSettings(archiveId).js5GroupSettings[groupId]?.fileSettings?.keys

    @ExperimentalUnsignedTypes
    public open fun readData(indexId: Int, containerId: Int): ByteBuffer = fileStore.read(indexId, containerId)

    @ExperimentalUnsignedTypes
    public open fun readGroup(
        archiveId: Int,
        groupId: Int,
        xteaKey: IntArray = XTEA_ZERO_KEY
    ): Js5Group {
        val archiveSettings = getArchiveSettings(archiveId)
        val groupSettings = archiveSettings.js5GroupSettings[groupId] ?: throw IOException("Js5Group does not exist.")
        val groupContainer = Js5Container.decode(readData(archiveId, groupId), xteaKey)
        logger.info("Reading archive $groupId from archive $archiveId")
        return Js5Group.decode(groupContainer, groupSettings)
    }

    @ExperimentalUnsignedTypes
    public open fun readGroup(
        archiveId: Int,
        groupName: String,
        xteaKey: IntArray = XTEA_ZERO_KEY
    ): Js5Group {
        val archiveSettings = getArchiveSettings(archiveId)
        val nameHash = groupName.hashCode()
        val groupSettings =  archiveSettings.js5GroupSettings.values.first { it.nameHash == nameHash }
        logger.info("Reading archive ${groupSettings.id} from archive $archiveId")
        val groupContainer = Js5Container.decode(readData(archiveId, groupSettings.id), xteaKey)
        return Js5Group.decode(groupContainer, groupSettings)
    }

    @ExperimentalUnsignedTypes
    public open fun readGroups(
        archiveId: Int,
        xteaKeys: Map<Int, IntArray> = emptyMap()
    ): Map<Int, Js5Group> {
        val archiveSettings = getArchiveSettings(archiveId)
        val groups = mutableMapOf<Int, Js5Group>()
        archiveSettings.js5GroupSettings.forEach { (groupId, groupSettings) ->
            val xtea = xteaKeys[groupId] ?: XTEA_ZERO_KEY
            logger.info("Reading group ${groupSettings.id} from archive $groupId")
            val archiveContainer = Js5Container.decode(readData(groupId, groupId), xtea)
            groups[groupId] = Js5Group.decode(archiveContainer, groupSettings)
        }
        return groups
    }

    @ExperimentalUnsignedTypes
    public open fun writeGroup(
        archiveId: Int,
        group: Js5Group,
        groupSegmentCount: Int = 1,
        groupSettingsVersion: Int? = null,
        groupContainerVersion: Int = -1,
        groupSettingsContainerVersion: Int = -1,
        groupXteaKey: IntArray = XTEA_ZERO_KEY,
        groupSettingsXteaKey: IntArray = XTEA_ZERO_KEY,
        groupCompression: Js5Compression = Js5Compression.NONE,
        groupSettingsCompression: Js5Compression = Js5Compression.NONE
    ) {
        if(archiveId > archiveSettings.size) throw IOException(
            "Can not create archive with id $archiveId, expected: ${archiveSettings.size}"
        )
        logger.info("Writing group ${group.id} from archive $archiveId")
        group.sizes?.compressed = writeGroupData(
            archiveId, group, groupSegmentCount, groupContainerVersion, groupXteaKey, groupCompression
        )
        writeGroupSettings(
            archiveId,
            group,
            groupSettingsVersion,
            groupSettingsContainerVersion,
            groupSettingsXteaKey,
            groupSettingsCompression
        )
    }

    @ExperimentalUnsignedTypes
    private fun writeGroupData(
        archiveId: Int,
        group: Js5Group,
        segmentCount: Int = 1,
        dataVersion: Int = -1,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        compression: Js5Compression = Js5Compression.NONE
    ): Int {
        logger.info("Writing group data for group ${group.id} from archive $archiveId")
        val versionedData = group.encode(segmentCount, dataVersion)
        val data = versionedData.encode(compression, xteaKey)
        fileStore.write(archiveId, group.id, data)
        return data.limit()
    }

    @ExperimentalUnsignedTypes
    private fun writeGroupSettings(
        archiveId: Int,
        group: Js5Group,
        settingsVersion: Int? = null,
        containerVersion: Int = -1,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        compression: Js5Compression = Js5Compression.NONE
    ) {
        logger.info("Writing group settings for group ${group.id} from archive $archiveId")
        val archiveSettings = if(archiveId == archiveSettings.size) {
            val settings = Js5ArchiveSettings(0, mutableMapOf())
            archiveSettings.add(archiveId, settings)
            settings
        } else {
            getArchiveSettings(archiveId)
        }
        if(settingsVersion == null) archiveSettings.version++ else archiveSettings.version = settingsVersion
        val fileSettings = mutableMapOf<Int, Js5FileSettings>()
        group.files.forEach { (id, file) ->
            fileSettings[id] = Js5FileSettings(id, file.nameHash)
        }
        if(!xteaKey.contentEquals(XTEA_ZERO_KEY)) settingsXtea[archiveId] = xteaKey
        archiveSettings.js5GroupSettings[group.id] = Js5GroupSettings(
            group.id,
            group.nameHash,
            group.crc,
            group.unknownHash,
            group.whirlpoolHash,
            group.sizes,
            group.version,
            fileSettings
        )
        fileStore.write(
            FileSystem.MASTER_INDEX,
            archiveId,
            archiveSettings.encode(containerVersion).encode(compression, xteaKey)
        )
    }

    @ExperimentalUnsignedTypes
    public fun generateChecksum(): Js5CacheChecksum {
        logger.info("Generating cache checksum")
        return Js5CacheChecksum(
            Array(archiveSettings.size) { archiveId ->
                val archiveSettings = archiveSettings[archiveId]
                val settingsData = fileStore.read(FileSystem.MASTER_INDEX, archiveId)
                ArchiveChecksum(
                    crc(settingsData),
                    archiveSettings.version,
                    archiveSettings.js5GroupSettings.size,
                    archiveSettings.js5GroupSettings.values
                        .sumBy { if(it.sizes?.compressed != null) it.sizes.uncompressed!! else 0 },
                    whirlPoolHash(settingsData.array())
                )
            }
        )
    }

    @ExperimentalUnsignedTypes
    private fun getArchiveSettings(archiveId: Int): Js5ArchiveSettings {
        if(archiveId !in 0..archiveSettings.size) throw IOException("Archive does not exist.")
        return archiveSettings[archiveId]
    }

    override fun close()  = fileStore.close()
}
