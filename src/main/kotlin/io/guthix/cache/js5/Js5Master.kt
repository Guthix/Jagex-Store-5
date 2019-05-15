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

import io.guthix.cache.js5.io.largeSmart
import io.guthix.cache.js5.io.uByte
import io.guthix.cache.js5.io.uShort
import io.guthix.cache.js5.io.writeLargeSmart
import io.guthix.cache.js5.container.Container
import io.guthix.cache.js5.util.WP_HASH_BYTE_COUNT
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

data class Js5ArchiveSettings(
    var version: Int,
    val js5GroupSettings: MutableMap<Int, Js5GroupSettings>
) {
    @ExperimentalUnsignedTypes
    internal fun encode(containerVersion: Int): Container {
        val byteStr = ByteArrayOutputStream()
        DataOutputStream(byteStr).use { os ->
            val format = if(version == -1) {
                Format.UNVERSIONED
            } else {
                if(js5GroupSettings.size <= UShort.MAX_VALUE.toInt()) {
                    Format.VERSIONED
                } else {
                    Format.VERSIONEDLARGE
                }
            }
            os.writeByte(format.opcode)
            if(format != Format.UNVERSIONED) os.writeInt(version)
            var flags = 0
            val hasNameHashes = js5GroupSettings.values.any { attr ->
                attr.nameHash != null || attr.fileSettings.values.any { file -> file.nameHash != null }
            }
            val hasUnknownHashes = js5GroupSettings.values.any { it.unknownHash != null }
            val hasWhirlPoolHashes = js5GroupSettings.values.any { it.whirlpoolHash != null }
            val hasSizes = js5GroupSettings.values.any { it.sizes != null }
            if(hasNameHashes) flags = flags or MASK_NAME_HASH
            if(hasUnknownHashes) flags = flags or MASK_UNKNOWN_HASH
            if(hasWhirlPoolHashes) flags = flags or MASK_WHIRLPOOL_HASH
            if(hasSizes) flags = flags or MASK_SIZES
            os.writeByte(flags)
            if(format == Format.VERSIONEDLARGE) {
                os.writeLargeSmart(js5GroupSettings.size)
            } else {
                os.writeShort(js5GroupSettings.size)
            }
            var prevArchiveId = 0
            for(id in js5GroupSettings.keys) {
                val delta = Math.abs(prevArchiveId - id)
                if(format == Format.VERSIONEDLARGE) os.writeLargeSmart(delta) else os.writeShort(delta)
                prevArchiveId = id
            }
            if(hasNameHashes) {
                for(attr in js5GroupSettings.values) os.writeInt(attr.nameHash ?: 0)
            }
            for(attr in js5GroupSettings.values) os.writeInt(attr.crc)
            if(hasUnknownHashes) {
                for(attr in js5GroupSettings.values) os.writeInt(attr.unknownHash ?: 0)
            }
            if(hasWhirlPoolHashes) {
                for(attr in js5GroupSettings.values) {
                    os.write(attr.whirlpoolHash ?: ByteArray(WP_HASH_BYTE_COUNT))
                }
            }
            if(hasSizes) {
                for(attr in js5GroupSettings.values) {
                    os.writeInt(attr.sizes?.compressed ?: 0)
                    os.writeInt(attr.sizes?.uncompressed ?: 0)
                }
            }
            for(attr in js5GroupSettings.values) {
                os.writeInt(attr.version)
            }
            for(attr in js5GroupSettings.values) {
                if(format == Format.VERSIONEDLARGE) {
                    os.writeLargeSmart(attr.fileSettings.size)
                } else {
                    os.writeShort(attr.fileSettings.size)
                }
            }
            for(attr in js5GroupSettings.values) {
                var prevFileId = 0
                for(id in attr.fileSettings.keys) {
                    val delta = Math.abs(prevFileId - id)
                    if(format == Format.VERSIONEDLARGE) {
                        os.writeLargeSmart(delta)
                    } else {
                        os.writeShort(delta)
                    }
                    prevFileId = id
                }
            }
            if(hasNameHashes) {
                for(attr in js5GroupSettings.values) {
                    for(file in attr.fileSettings.values) {
                        os.writeInt(file.nameHash ?: 0)
                    }
                }
            }
        }
        return Container(containerVersion, ByteBuffer.wrap(byteStr.toByteArray()))
    }

    enum class Format(val opcode: Int) { UNVERSIONED(5), VERSIONED(6), VERSIONEDLARGE(7) }

    companion object {
        private const val MASK_NAME_HASH = 0x01
        private const val MASK_WHIRLPOOL_HASH = 0x02
        private const val MASK_SIZES = 0x04
        private const val MASK_UNKNOWN_HASH = 0x08

        @ExperimentalUnsignedTypes
        internal fun decode(container: Container): Js5ArchiveSettings {
            val buffer = container.data
            val formatOpcode = buffer.uByte.toInt()
            val format = Format.values().find { it.opcode == formatOpcode }
            require(format != null)
            val version = if (format == Format.UNVERSIONED) 0 else buffer.int
            val flags = buffer.uByte.toInt()
            val groupCount = if (format == Format.VERSIONEDLARGE) buffer.largeSmart else buffer.uShort.toInt()
            val groupIds = IntArray(groupCount)
            var groupAccumulator = 0
            for(archiveIndex in groupIds.indices) {
                // difference with previous id
                val delta = if (format == Format.VERSIONEDLARGE) buffer.largeSmart else buffer.uShort.toInt()
                groupAccumulator += delta
                groupIds[archiveIndex] = groupAccumulator
            }
            val groupNameHashes = if (flags and MASK_NAME_HASH != 0) IntArray(groupCount) {
                buffer.int
            } else null
            val groupCRCs = IntArray(groupCount) { buffer.int }
            val groupUnkownHashes = if (flags and MASK_UNKNOWN_HASH != 0) IntArray(groupCount) {
                buffer.int
            } else null
            val groupWhirlpoolHashes = if (flags and MASK_WHIRLPOOL_HASH != 0) Array(groupCount) {
                ByteArray(WP_HASH_BYTE_COUNT) { buffer.get() }
            } else null
            val groupSizes = if(flags and MASK_SIZES != 0) Array(groupCount) {
                Js5GroupSettings.Size(compressed = buffer.int, uncompressed = buffer.int)
            } else null
            val groupVersions = Array(groupCount) { buffer.int }
            val groupFileIds = Array(groupCount) {
                // decodeMultiFileContainer file count
                IntArray(if (format == Format.VERSIONEDLARGE) buffer.largeSmart else buffer.uShort.toInt())
            }
            for(group in groupFileIds) {
                var fileIdAccumulator = 0
                for(fileIndex in group.indices) {
                    // difference with previous id
                    val delta = if (format == Format.VERSIONEDLARGE) buffer.largeSmart else buffer.uShort.toInt()
                    fileIdAccumulator += delta
                    group[fileIndex] = fileIdAccumulator
                }
            }
            val groupFileNameHashes = if (flags and MASK_NAME_HASH != 0) {
                Array(groupCount) {
                    IntArray(groupFileIds[it].size) {
                        buffer.int
                    }
                }
            } else null

            val groupSettings = mutableMapOf<Int, Js5GroupSettings>()
            for(groupIndex in groupIds.indices) {
                val fileSettings = mutableMapOf<Int, Js5FileSettings>()
                for(fileIndex in groupFileIds[groupIndex].indices) {
                    fileSettings[groupFileIds[groupIndex][fileIndex]] = Js5FileSettings(
                        groupFileIds[groupIndex][fileIndex],
                        groupFileNameHashes?.get(groupIndex)?.get(fileIndex)
                    )
                }
                groupSettings[groupIds[groupIndex]] = Js5GroupSettings(
                    groupIds[groupIndex],
                    groupNameHashes?.get(groupIndex),
                    groupCRCs[groupIndex],
                    groupUnkownHashes?.get(groupIndex),
                    groupWhirlpoolHashes?.get(groupIndex),
                    groupSizes?.get(groupIndex),
                    groupVersions[groupIndex],
                    fileSettings
                )
            }
            return Js5ArchiveSettings(version, groupSettings)
        }
    }
}

data class Js5GroupSettings(
    val id: Int,
    val nameHash: Int?,
    val crc: Int,
    val unknownHash: Int?,
    val whirlpoolHash: ByteArray?,
    val sizes: Size?,
    val version: Int,
    val fileSettings: MutableMap<Int, Js5FileSettings>

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5GroupSettings
        if (id != other.id) return false
        if (nameHash != other.nameHash) return false
        if (crc != other.crc) return false
        if (unknownHash != other.unknownHash) return false
        if (whirlpoolHash != null) {
            if (other.whirlpoolHash == null) return false
            if (!whirlpoolHash.contentEquals(other.whirlpoolHash)) return false
        } else if (other.whirlpoolHash != null) return false
        if (sizes != other.sizes) return false
        if (version != other.version) return false
        if (fileSettings != other.fileSettings) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + (nameHash ?: 0)
        result = 31 * result + crc
        result = 31 * result + (unknownHash ?: 0)
        result = 31 * result + (whirlpoolHash?.contentHashCode() ?: 0)
        result = 31 * result + (sizes?.hashCode() ?: 0)
        result = 31 * result + version
        result = 31 * result + fileSettings.hashCode()
        return result
    }

    data class Size(var compressed: Int?, val uncompressed: Int?)
}

data class Js5FileSettings(val id: Int, val nameHash: Int?)
