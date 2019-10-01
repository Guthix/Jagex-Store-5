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

import io.guthix.buffer.readLargeSmart
import io.guthix.buffer.writeLargeSmart
import io.guthix.cache.js5.container.Js5Compression
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.Uncompressed
import io.guthix.cache.js5.container.XTEA_ZERO_KEY
import io.guthix.cache.js5.util.WHIRLPOOL_HASH_SIZE
import io.guthix.cache.js5.util.crc
import io.guthix.cache.js5.util.whirlPoolHash
import io.netty.buffer.Unpooled
import java.io.IOException
import kotlin.math.abs

/**
 * An Archive in the cache.
 */
data class Js5Archive(val id: Int, val groups: MutableMap<Int, Js5Group>, var version: Int?)

/**
 * The settings for a [Js5Archive].
 *
 * Archive settings are stored in the master index(.idx255) and contain meta-data about archives.
 *
 * @property version (Optional) The version of the archive settings.
 * @property js5GroupSettings A map of [Js5GroupSettings] indexed by their id.
 */
data class Js5ArchiveSettings(
    var version: Int?,
    val js5GroupSettings: MutableMap<Int, Js5GroupSettings>,
    val containsNameHash: Boolean,
    val containsWpHash: Boolean,
    val containsSizes: Boolean,
    val containsUnknownHash: Boolean,
    var xteaKey: IntArray = XTEA_ZERO_KEY,
    var compression: Js5Compression = Uncompressed()
) {
    val uncompressedSize get() = js5GroupSettings.values.sumBy {
        it.sizes?.uncompressed ?: 0
    }

    fun calculateChecksum(): Js5ArchiveChecksum {
        val rawData = encode().encode()
        return Js5ArchiveChecksum(
            rawData.crc(), version, js5GroupSettings.size, uncompressedSize, rawData.whirlPoolHash()
        )
    }

    /**
     * Encodes the [Js5ArchiveSettings] into a [Js5Container].
     */
    fun encode(): Js5Container {
        val buf = Unpooled.buffer()
        val format = if(this.version == -1) {
            Format.UNVERSIONED
        } else {
            if(js5GroupSettings.size <= UShort.MAX_VALUE.toInt()) {
                Format.VERSIONED
            } else {
                Format.VERSIONED_LARGE
            }
        }
        buf.writeByte(format.opcode)
        if(format != Format.UNVERSIONED) buf.writeInt(this.version!!)
        var flags = 0
        if(containsNameHash) flags = flags or MASK_NAME_HASH
        if(containsUnknownHash) flags = flags or MASK_UNKNOWN_HASH
        if(containsWpHash) flags = flags or MASK_WHIRLPOOL_HASH
        if(containsSizes) flags = flags or MASK_SIZES
        buf.writeByte(flags)
        if(format == Format.VERSIONED_LARGE) {
            buf.writeLargeSmart(js5GroupSettings.size)
        } else {
            buf.writeShort(js5GroupSettings.size)
        }
        var prevArchiveId = 0
        for(id in js5GroupSettings.keys) {
            val delta = abs(prevArchiveId - id)
            if(format == Format.VERSIONED_LARGE) buf.writeLargeSmart(delta) else buf.writeShort(delta)
            prevArchiveId = id
        }
        if(containsNameHash) {
            for(attr in js5GroupSettings.values) buf.writeInt(attr.nameHash ?: 0)
        }
        for(attr in js5GroupSettings.values) buf.writeInt(attr.crc)
        if(containsUnknownHash) {
            for(attr in js5GroupSettings.values) buf.writeInt(attr.unknownHash ?: 0)
        }
        if(containsWpHash) {
            for(attr in js5GroupSettings.values) {
                buf.writeBytes(attr.whirlpoolHash ?: ByteArray(WHIRLPOOL_HASH_SIZE))
            }
        }
        if(containsSizes) {
            for(attr in js5GroupSettings.values) {
                buf.writeInt(attr.sizes?.compressed ?: 0)
                buf.writeInt(attr.sizes?.uncompressed ?: 0)
            }
        }
        for(attr in js5GroupSettings.values) {
            buf.writeInt(attr.version)
        }
        for(attr in js5GroupSettings.values) {
            if(format == Format.VERSIONED_LARGE) {
                buf.writeLargeSmart(attr.fileSettings.size)
            } else {
                buf.writeShort(attr.fileSettings.size)
            }
        }
        for(attr in js5GroupSettings.values) {
            var prevFileId = 0
            for(id in attr.fileSettings.keys) {
                val delta = abs(prevFileId - id)
                if(format == Format.VERSIONED_LARGE) {
                    buf.writeLargeSmart(delta)
                } else {
                    buf.writeShort(delta)
                }
                prevFileId = id
            }
        }
        if(containsNameHash) {
            for(attr in js5GroupSettings.values) {
                for(file in attr.fileSettings.values) {
                    buf.writeInt(file.nameHash ?: 0)
                }
            }
        }
        return Js5Container(buf, xteaKey, compression, null) // settings containers don't have versions
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Js5ArchiveSettings

        if (version != other.version) return false
        if (js5GroupSettings != other.js5GroupSettings) return false
        if (compression != other.compression) return false
        if (!xteaKey.contentEquals(other.xteaKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version ?: 0
        result = 31 * result + js5GroupSettings.hashCode()
        result = 31 * result + compression.hashCode()
        result = 31 * result + xteaKey.contentHashCode()
        return result
    }

    /**
     * Different types of [Js5ArchiveSettings]
     *
     * @property opcode The opcode used to encode the format.
     */
    enum class Format(val opcode: Int) { UNVERSIONED(5), VERSIONED(6), VERSIONED_LARGE(7) }

    companion object {
        private const val MASK_NAME_HASH = 0x01
        private const val MASK_WHIRLPOOL_HASH = 0x02
        private const val MASK_SIZES = 0x04
        private const val MASK_UNKNOWN_HASH = 0x08

        /**
         * Decodes the [Js5Container] into a [Js5ArchiveSettings].
         */
        fun decode(container: Js5Container): Js5ArchiveSettings {
            val buf = container.data
            val formatOpcode = buf.readUnsignedByte().toInt()
            val format = Format.values().firstOrNull { it.opcode == formatOpcode } ?: throw IOException(
                "Settings format $formatOpcode not supported."
            )
            val version = if (format == Format.UNVERSIONED) null else buf.readInt()
            val flags = buf.readUnsignedByte().toInt()
            val containsNameHash = flags and MASK_NAME_HASH != 0
            val containsWpHash = flags and MASK_WHIRLPOOL_HASH != 0
            val containsSizes = flags and MASK_SIZES != 0
            val containsUnknownHash = flags and MASK_UNKNOWN_HASH != 0
            val groupCount = if (format == Format.VERSIONED_LARGE) buf.readLargeSmart() else buf.readUnsignedShort()
            val groupIds = IntArray(groupCount)
            var groupAccumulator = 0
            for(archiveIndex in groupIds.indices) {
                val delta = if (format == Format.VERSIONED_LARGE) buf.readLargeSmart() else buf.readUnsignedShort()
                groupAccumulator += delta
                groupIds[archiveIndex] = groupAccumulator
            }
            val groupNameHashes = if (containsNameHash) IntArray(groupCount) {
                buf.readInt()
            } else null
            val gropuCrcs = IntArray(groupCount) { buf.readInt() }
            val groupUnkownHashes = if (containsUnknownHash) IntArray(groupCount) {
                buf.readInt()
            } else null
            val groupWhirlpoolHashes = if (containsWpHash) Array(groupCount) {
                ByteArray(WHIRLPOOL_HASH_SIZE) { buf.readByte() }
            } else null
            val groupSizes = if(containsSizes) Array(groupCount) {
                Js5Container.Size(compressed = buf.readInt(), uncompressed = buf.readInt())
            } else null
            val groupVersions = Array(groupCount) { buf.readInt() }
            val groupFileIds = Array(groupCount) {
                IntArray(if (format == Format.VERSIONED_LARGE) buf.readLargeSmart() else buf.readUnsignedShort())
            }
            for(group in groupFileIds) {
                var fileIdAccumulator = 0
                for(fileIndex in group.indices) {
                    // difference with previous id
                    val delta = if (format == Format.VERSIONED_LARGE) buf.readLargeSmart() else buf.readUnsignedShort()
                    fileIdAccumulator += delta
                    group[fileIndex] = fileIdAccumulator
                }
            }
            val groupFileNameHashes = if (containsNameHash) {
                Array(groupCount) {
                    IntArray(groupFileIds[it].size) {
                        buf.readInt()
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
                    groupVersions[groupIndex],
                    gropuCrcs[groupIndex],
                    fileSettings,
                    groupNameHashes?.get(groupIndex),
                    groupUnkownHashes?.get(groupIndex),
                    groupWhirlpoolHashes?.get(groupIndex),
                    groupSizes?.get(groupIndex)

                )
            }
            return Js5ArchiveSettings(version, groupSettings, containsNameHash, containsWpHash, containsSizes,
                containsUnknownHash, container.xteaKey, container.compression
            )
        }
    }
}

/**
 * The checksum meta-data for an archive.
 *
 * @property crc The [java.util.zip.CRC32] value of the [Js5ArchiveSettings] as an encoded [Js5Container].
 * @property version (Optional) he version of the [Js5ArchiveSettings].
 * @property fileCount The amount of [Js5File] in the archive.
 * @property uncompressedSize The size of the sum of all [Js5GroupData] data uncompressed.
 * @property whirlpoolDigest (Optional) The whirlpool digest of this archive.
 */
data class Js5ArchiveChecksum(
    var crc: Int,
    var version: Int?,
    var fileCount: Int?,
    var uncompressedSize: Int?,
    var whirlpoolDigest: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5ArchiveChecksum
        if (crc != other.crc) return false
        if (version != other.version) return false
        if (fileCount != other.fileCount) return false
        if (uncompressedSize != other.uncompressedSize) return false
        if (whirlpoolDigest != null) {
            if (other.whirlpoolDigest == null) return false
            if (!whirlpoolDigest!!.contentEquals(other.whirlpoolDigest!!)) return false
        } else if (other.whirlpoolDigest != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = crc
        result = 31 * result + (version ?: 0)
        result = 31 * result + (fileCount ?: 0)
        result = 31 * result + (uncompressedSize ?: 0)
        result = 31 * result + (whirlpoolDigest?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        internal const val ENCODED_SIZE = Int.SIZE_BYTES + Int.SIZE_BYTES
        internal const val WP_ENCODED_SIZE = ENCODED_SIZE + Int.SIZE_BYTES + Int.SIZE_BYTES + WHIRLPOOL_HASH_SIZE
    }
}


