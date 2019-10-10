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
import io.guthix.cache.js5.container.disk.IdxFile
import io.guthix.cache.js5.container.disk.Index
import io.guthix.cache.js5.container.disk.Js5DiskStore
import io.guthix.cache.js5.util.WHIRLPOOL_HASH_SIZE
import io.guthix.cache.js5.util.crc
import io.guthix.cache.js5.util.whirlPoolHash
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.io.IOException
import kotlin.math.abs

private val logger = KotlinLogging.logger { }

/**
 * An rchive in the cache. The [Js5Archive] class can be used for reading, writing anr removing [Js5Group]s from the
 * cache. After working with the [Js5Archive] it is required to call the [Js5Archive.close] method to confirm all the
 * modifications.
 *
 * @property version The version of the archive.
 * @property containsNameHash Whether this archive contains names.
 * @property containsWpHash Whether this archive contains whirlpool hashes.
 * @property containsSizes Whether this archive contains the [Js5Container.Size] in the [Js5ArchiveSettings].
 * @property containsUnknownHash Whether this archive contains an yet unknown hash.
 * @property xteaKey The XTEA key to decrypt the [Js5ArchiveSettings].
 * @property compression The [Js5Compression] used to store the [Js5ArchiveSettings].
 * @property groupSettings The [Js5GroupSettings] which contains settings for all the [Js5Group]s.
 * @property store The [Js5DiskStore] this archive belongs to.
 * @property indexFile The [IdxFile] that contains the [Index]es for this [Js5Archive].
 */
data class Js5Archive internal constructor(
    var version: Int? = null,
    val containsNameHash: Boolean = false,
    val containsWpHash: Boolean = false,
    val containsSizes: Boolean = false,
    val containsUnknownHash: Boolean = false,
    var xteaKey: IntArray = XTEA_ZERO_KEY,
    var compression: Js5Compression = Uncompressed(),
    val groupSettings: MutableMap<Int, Js5GroupSettings> = mutableMapOf(),
    private val store: Js5DiskStore,
    private val indexFile: IdxFile
) : AutoCloseable {
    /**
     * The unique id of the archive.
     */
    val id get() = indexFile.id

    /**
     * The [Js5ArchiveSettings] belonging to this [Js5Archive].
     */
    private val archiveSettings get() = Js5ArchiveSettings(
        version, containsNameHash, containsWpHash, containsSizes, containsUnknownHash, groupSettings
    )

    /**
     * Reads a [Js5Group] from this [Js5Archive] by id.
     *
     * @param groupId The group id to read.
     * @param xteaKey The XTEA encryption key used to encrypt this group.
     */
    fun readGroup(groupId: Int, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Group {
        val settings = groupSettings.getOrElse(groupId) {
            throw IllegalArgumentException("Unable to read group $groupId because it does not exist.")
        }
        return readGroup(indexFile.id, settings, xteaKey)
    }

    /**
     * Reads a [Js5Group] from this [Js5Archive] by name. This method should only be called if [containsNameHash] is
     * true.
     *
     * @param groupName The group name to read.
     * @param xteaKey The XTEA encryption key used to encrypt this group.
     */
    fun readGroup(groupName: String, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Group {
        check(containsNameHash) {
            "Unable to read group by name because the archive does not contain name hashes."
        }
        val nameHash = groupName.hashCode()
        val settings =  groupSettings.values.firstOrNull { it.nameHash == nameHash }
            ?: throw IllegalArgumentException("Unable to read group `$groupName` because it does not exist.")
        return readGroup(indexFile.id, settings, xteaKey)
    }

    /**
     * Reads a group from the [store] using the [Js5GroupSettings].
     */
    private fun readGroup(archiveId: Int, groupSettings: Js5GroupSettings, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Group {
        val groupData = Js5GroupData.decode(
            Js5Container.decode(store.read(indexFile, groupSettings.id), xteaKey),
            groupSettings.fileSettings.size
        )
        val group = Js5Group.create(groupData, groupSettings)
        logger.info("Reading group ${groupSettings.id} from archive $archiveId")
        return group
    }

    /**
     * Writes a [Js5Group] to this [Js5Archive].
     *
     * @param group The [Js5Group] to write.
     * @param appendVersion Whether to append the version to the [Js5Container].
     */
    fun writeGroup(group: Js5Group, appendVersion: Boolean) {
        val container = group.groupData.encode(if(appendVersion) group.version else null)
        val uncompressedSize = container.data.writerIndex()
        val data = container.encode()
        val compressedSize = if(appendVersion) data.writerIndex() - 2 else data.writerIndex()
        group.crc = data.crc(length = compressedSize)
        if(containsWpHash) group.whirlpoolHash = data.whirlPoolHash(length = compressedSize)
        writeGroupData(group.id, data)
        if(containsSizes) group.sizes = Js5Container.Size(compressedSize, uncompressedSize)
        groupSettings[group.id] = group.groupSettings
    }

    /**
     * Removes a [Js5Group] from this [Js5Archive].
     */
    fun removeGroup(groupId: Int) {
        groupSettings.remove(groupId) ?: throw IllegalArgumentException(
            "Unable to remove group $groupId from archive ${indexFile.id} because the group does not exist."
        )
        store.remove(indexFile, groupId)
    }

    /**
     * Writes the group [Js5Container] data to the [store].
     */
    private fun writeGroupData(groupId: Int, data: ByteBuf): Int {
        val compressedSize = data.readableBytes()
        store.write(indexFile, groupId, data)
        return compressedSize
    }

    override fun close() {
        val settings = archiveSettings
        logger.debug { "Writing archive settings for archive ${indexFile.id}" }
        store.write(store.masterIdxFile, indexFile.id, settings.encode(xteaKey, compression).encode())
        indexFile.close()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Js5Archive

        if (version != other.version) return false
        if (containsNameHash != other.containsNameHash) return false
        if (containsWpHash != other.containsWpHash) return false
        if (containsSizes != other.containsSizes) return false
        if (containsUnknownHash != other.containsUnknownHash) return false
        if (!xteaKey.contentEquals(other.xteaKey)) return false
        if (compression != other.compression) return false
        if (groupSettings != other.groupSettings) return false
        if (store != other.store) return false
        if (indexFile != other.indexFile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version ?: 0
        result = 31 * result + containsNameHash.hashCode()
        result = 31 * result + containsWpHash.hashCode()
        result = 31 * result + containsSizes.hashCode()
        result = 31 * result + containsUnknownHash.hashCode()
        result = 31 * result + xteaKey.contentHashCode()
        result = 31 * result + compression.hashCode()
        result = 31 * result + groupSettings.hashCode()
        result = 31 * result + store.hashCode()
        result = 31 * result + indexFile.hashCode()
        return result
    }

    companion object {
        internal fun create(
            store: Js5DiskStore,
            indexFile: IdxFile,
            settings: Js5ArchiveSettings,
            xteaKey: IntArray,
            compression: Js5Compression
        ) = Js5Archive(settings.version, settings.containsNameHash, settings.containsWpHash, settings.containsSizes,
            settings.containsUnknownHash, xteaKey, compression, settings.groupSettings, store, indexFile
        )
    }
}

/**
 * The settings for a [Js5Archive]. The [Js5ArchiveSettings] contain meta data about [Js5Archive]s and the [Js5Group]s
 * belonging to those archives.
 *
 * @property version (Optional) The version of the archive settings.
 * @property containsNameHash Whether this archive contains names.
 * @property containsWpHash Whether this archive contains whirlpool hashes.
 * @property containsSizes Whether this archive contains the [Js5Container.Size] in the [Js5ArchiveSettings].
 * @property containsUnknownHash Whether this archive contains an yet unknown hash.
 * @property groupSettings A map of [Js5GroupSettings] indexed by their id.
 */
data class Js5ArchiveSettings(
    var version: Int?,
    val containsNameHash: Boolean,
    val containsWpHash: Boolean,
    val containsSizes: Boolean,
    val containsUnknownHash: Boolean,
    val groupSettings: MutableMap<Int, Js5GroupSettings> = mutableMapOf()
) {
    /**
     * Encodes the [Js5ArchiveSettings] into a [Js5Container].
     */
    fun encode(xteaKey: IntArray = XTEA_ZERO_KEY, compression: Js5Compression = Uncompressed()): Js5Container {
        val buf = Unpooled.buffer()
        val format = if(this.version == -1) {
            Format.UNVERSIONED
        } else {
            if(groupSettings.size <= 0xFFFF) {
                Format.VERSIONED
            } else {
                Format.VERSIONED_LARGE
            }
        }
        buf.writeByte(format.opcode)
        if(format != Format.UNVERSIONED) buf.writeInt(version!!)
        var flags = 0
        if(containsNameHash) flags = flags or MASK_NAME_HASH
        if(containsUnknownHash) flags = flags or MASK_UNKNOWN_HASH
        if(containsWpHash) flags = flags or MASK_WHIRLPOOL_HASH
        if(containsSizes) flags = flags or MASK_SIZES
        buf.writeByte(flags)
        if(format == Format.VERSIONED_LARGE) {
            buf.writeLargeSmart(groupSettings.size)
        } else {
            buf.writeShort(groupSettings.size)
        }
        var prevArchiveId = 0
        for(id in groupSettings.keys) {
            val delta = abs(prevArchiveId - id)
            if(format == Format.VERSIONED_LARGE) buf.writeLargeSmart(delta) else buf.writeShort(delta)
            prevArchiveId = id
        }
        if(containsNameHash) {
            for(attr in groupSettings.values) buf.writeInt(attr.nameHash ?: 0)
        }
        for(attr in groupSettings.values) buf.writeInt(attr.crc)
        if(containsUnknownHash) {
            for(attr in groupSettings.values) buf.writeInt(attr.unknownHash ?: 0)
        }
        if(containsWpHash) {
            for(attr in groupSettings.values) {
                buf.writeBytes(attr.whirlpoolHash ?: ByteArray(WHIRLPOOL_HASH_SIZE))
            }
        }
        if(containsSizes) {
            for(attr in groupSettings.values) {
                buf.writeInt(attr.sizes?.compressed ?: 0)
                buf.writeInt(attr.sizes?.uncompressed ?: 0)
            }
        }
        for(attr in groupSettings.values) {
            buf.writeInt(attr.version)
        }
        for(attr in groupSettings.values) {
            if(format == Format.VERSIONED_LARGE) {
                buf.writeLargeSmart(attr.fileSettings.size)
            } else {
                buf.writeShort(attr.fileSettings.size)
            }
        }
        for(attr in groupSettings.values) {
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
            for(attr in groupSettings.values) {
                for(file in attr.fileSettings.values) {
                    buf.writeInt(file.nameHash ?: 0)
                }
            }
        }
        return Js5Container(buf, xteaKey, compression, null) // settings containers don't have versions
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
                "Archive Settings format $formatOpcode not supported."
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
            return Js5ArchiveSettings(
                version, containsNameHash, containsWpHash, containsSizes, containsUnknownHash, groupSettings
            )
        }
    }
}

/**
 * The validator that can be used to check whether an archive is not corrupted or outdated.
 *
 * @property crc The [java.util.zip.CRC32] value of the [Js5ArchiveSettings] as an encoded [Js5Container].
 * @property version (Optional) he version of the [Js5ArchiveSettings].
 * @property fileCount (Optional) The amount of [Js5File]s in the archive.
 * @property uncompressedSize (Optional) The size of the sum of all [Js5GroupData] data uncompressed.
 * @property whirlpoolDigest (Optional) The whirlpool digest of this archive.
 */
data class Js5ArchiveValidator(
    var crc: Int,
    var version: Int?,
    var fileCount: Int?,
    var uncompressedSize: Int?,
    var whirlpoolDigest: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5ArchiveValidator
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
        /**
         * Size required to encode the [Js5ArchiveValidator] in the old format without whirlpool.
         */
        internal const val ENCODED_SIZE = Int.SIZE_BYTES + Int.SIZE_BYTES

        /**
         * Size required to encode the [Js5ArchiveValidator] in the old format with whirlpool.
         */
        internal const val WP_ENCODED_SIZE = ENCODED_SIZE + WHIRLPOOL_HASH_SIZE

        /**
         * Size required to encode the [Js5ArchiveValidator] in the new format without whirlpool.
         */
        internal const val ENCODED_SIZE_WP_NEW = WP_ENCODED_SIZE + Int.SIZE_BYTES + Int.SIZE_BYTES
    }
}


