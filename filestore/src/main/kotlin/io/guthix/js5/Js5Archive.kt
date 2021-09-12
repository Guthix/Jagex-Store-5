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
package io.guthix.js5

import io.guthix.buffer.readUnsignedIntSmart
import io.guthix.buffer.writeUnsignedIntSmart
import io.guthix.js5.container.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.io.IOException
import kotlin.math.abs

private val logger = KotlinLogging.logger { }

/**
 * An archive in the cache. The [Js5Archive] class can be used for reading, writing anr removing [Js5Group]s from the
 * cache. After writing a group to the [Js5Archive] it is recommended to write the [Js5Archive] to the [Js5Cache].
 *
 * @property id The archive id of this archive.
 * @property version The version of the archive.
 * @property containsNameHash Whether this archive contains names.
 * @property containsWpHash Whether this archive contains whirlpool hashes.
 * @property containsSizes Whether this archive contains the [Js5Container.Size] in the [Js5ArchiveSettings].
 * @property containsUnknownHash Whether this archive contains an yet unknown hash.
 * @property compression The [Js5Compression] used to store the [Js5ArchiveSettings].
 * @property groupSettings The [Js5GroupSettings] which contains settings for all the [Js5Group]s.
 * @property readStore The [Js5ReadStore] where all read operations are done.
 * @property writeStore The [Js5WriteStore] where all the write operations are done.
 */
public data class Js5Archive internal constructor(
    val id: Int,
    var version: Int? = null,
    val containsNameHash: Boolean = false,
    val containsWpHash: Boolean = false,
    val containsSizes: Boolean = false,
    val containsUnknownHash: Boolean = false,
    var compression: Js5Compression = Uncompressed,
    val groupSettings: MutableMap<Int, Js5GroupSettings> = mutableMapOf(),
    private val readStore: Js5ReadStore,
    private val writeStore: Js5WriteStore?
) {
    /**
     * The [Js5ArchiveSettings] belonging to this [Js5Archive].
     */
    val archiveSettings: Js5ArchiveSettings get() = Js5ArchiveSettings(
        version, containsNameHash, containsWpHash, containsSizes, containsUnknownHash, groupSettings
    )

    /**
     * Reads a [Js5Group] from this [Js5Archive] by id.
     *
     * @param groupId The group id to read.
     * @param xteaKey The XTEA encryption key used to encrypt this group.
     */
    public fun readGroup(groupId: Int, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Group {
        val settings = groupSettings.getOrElse(groupId) {
            throw IllegalArgumentException("Unable to read group $groupId because it does not exist.")
        }
        return readGroup(id, settings, xteaKey)
    }

    /**
     * Reads a [Js5Group] from this [Js5Archive] by name. This method should only be called if [containsNameHash] is
     * true.
     *
     * @param groupName The group name to read.
     * @param xteaKey The XTEA encryption key used to encrypt this group.
     */
    public fun readGroup(groupName: String, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Group {
        check(containsNameHash) {
            "Unable to read group by name because the archive does not contain name hashes."
        }
        val nameHash = groupName.hashCode()
        val settings = groupSettings.values.firstOrNull { it.nameHash == nameHash }
            ?: throw IllegalArgumentException("Unable to read group `$groupName` because it does not exist.")
        return readGroup(id, settings, xteaKey)
    }

    /**
     * Reads a group from the [readStore] using the [Js5GroupSettings].
     */
    private fun readGroup(
        archiveId: Int,
        groupSettings: Js5GroupSettings,
        xteaKey: IntArray = XTEA_ZERO_KEY
    ): Js5Group {
        val groupData = Js5GroupData.decode(
            Js5Container.decode(readStore.read(id, groupSettings.id), xteaKey),
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
     * @param xteaKey The encryption key to use.
     * @param autoVersion Whether to auto increment the version number.
     * @param appendVersion Whether to append the version to the [Js5Container].
     */
    public fun writeGroup(
        group: Js5Group,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        autoVersion: Boolean = true,
        appendVersion: Boolean = true
    ) {
        if(autoVersion) group.version++
        val container = group.groupData.encode(if (appendVersion) group.version else null)
        val uncompressedSize = container.data.writerIndex()
        val data = container.encode(xteaKey)
        val compressedSize = if (appendVersion) data.writerIndex() - 2 else data.writerIndex()
        group.compressedCrc = data.crc(length = compressedSize)
        if (containsWpHash) group.whirlpoolHash = data.whirlPoolHash(length = compressedSize)
        writeGroupData(group.id, data)
        if (containsSizes) group.sizes = Js5Container.Size(compressedSize, uncompressedSize)
        groupSettings[group.id] = group.groupSettings
    }

    /** Writes the group [Js5Container] data to the [writeStore]. */
    private fun writeGroupData(groupId: Int, data: ByteBuf): Int {
        writeStore ?: error("No Js5WriteStore provided.")
        val compressedSize = data.readableBytes()
        writeStore.write(id, groupId, data)
        return compressedSize
    }

    /** Removes a [Js5Group] from this [Js5Archive]. */
    public fun removeGroup(groupId: Int) {
        writeStore ?: error("No Js5WriteStore provided.")
        groupSettings.remove(groupId) ?: throw IllegalArgumentException(
            "Unable to remove group $groupId from archive $id because the group does not exist."
        )
        writeStore.remove(id, groupId)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5Archive
        if (id != other.id) return false
        if (version != other.version) return false
        if (containsNameHash != other.containsNameHash) return false
        if (containsWpHash != other.containsWpHash) return false
        if (containsSizes != other.containsSizes) return false
        if (containsUnknownHash != other.containsUnknownHash) return false
        if (compression != other.compression) return false
        if (groupSettings != other.groupSettings) return false
        if (readStore != other.readStore) return false
        if (writeStore != other.writeStore) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + (version ?: 0)
        result = 31 * result + containsNameHash.hashCode()
        result = 31 * result + containsWpHash.hashCode()
        result = 31 * result + containsSizes.hashCode()
        result = 31 * result + containsUnknownHash.hashCode()
        result = 31 * result + compression.hashCode()
        result = 31 * result + groupSettings.hashCode()
        result = 31 * result + readStore.hashCode()
        result = 31 * result + writeStore.hashCode()
        return result
    }

    internal companion object {
        internal fun create(
            id: Int,
            settings: Js5ArchiveSettings,
            compression: Js5Compression,
            readStore: Js5ReadStore,
            writeStore: Js5WriteStore?
        ) = Js5Archive(id, settings.version, settings.containsNameHash, settings.containsWpHash, settings.containsSizes,
            settings.containsUncompressedCrc, compression, settings.groupSettings, readStore, writeStore
        )
    }
}

/**
 * The settings for a [Js5Archive]. The [Js5ArchiveSettings] contain meta-data about [Js5Archive]s and the [Js5Group]s
 * belonging to those archives.
 *
 * @property version (Optional) The version of the archive settings.
 * @property containsNameHash Whether this archive contains names.
 * @property containsWpHash Whether this archive contains whirlpool hashes.
 * @property containsSizes Whether this archive contains the [Js5Container.Size] in the [Js5ArchiveSettings].
 * @property containsUncompressedCrc Whether this archive contains an yet unknown hash.
 * @property groupSettings A map of [Js5GroupSettings] indexed by their id.
 */
public data class Js5ArchiveSettings(
    var version: Int?,
    val containsNameHash: Boolean,
    val containsWpHash: Boolean,
    val containsSizes: Boolean,
    val containsUncompressedCrc: Boolean,
    val groupSettings: MutableMap<Int, Js5GroupSettings> = mutableMapOf()
) {
    /**
     * Encodes the [Js5ArchiveSettings] into a [Js5Container].
     */
    public fun encode(compression: Js5Compression = Uncompressed): Js5Container {
        val buf = Unpooled.buffer()
        val format = if (version == -1) {
            Format.UNVERSIONED
        } else {
            if (groupSettings.size <= 0xFFFF) {
                Format.VERSIONED
            } else {
                Format.VERSIONED_LARGE
            }
        }
        buf.writeByte(format.opcode)
        if (format != Format.UNVERSIONED) buf.writeInt(version!!)
        var flags = 0
        if (containsNameHash) flags = flags or MASK_NAME_HASH
        if (containsUncompressedCrc) flags = flags or MASK_UNC_CRC
        if (containsWpHash) flags = flags or MASK_WHIRLPOOL_HASH
        if (containsSizes) flags = flags or MASK_SIZES
        buf.writeByte(flags)
        if (format == Format.VERSIONED_LARGE) {
            buf.writeUnsignedIntSmart(groupSettings.size)
        } else {
            buf.writeShort(groupSettings.size)
        }
        var prevArchiveId = 0
        for (id in groupSettings.keys) {
            val delta = abs(prevArchiveId - id)
            if (format == Format.VERSIONED_LARGE) buf.writeUnsignedIntSmart(delta) else buf.writeShort(delta)
            prevArchiveId = id
        }
        if (containsNameHash) {
            for (attr in groupSettings.values) buf.writeInt(attr.nameHash ?: 0)
        }
        for (attr in groupSettings.values) buf.writeInt(attr.compressedCrc)
        if (containsUncompressedCrc) {
            for (attr in groupSettings.values) buf.writeInt(attr.uncompressedCrc ?: 0)
        }
        if (containsWpHash) {
            for (attr in groupSettings.values) {
                buf.writeBytes(attr.whirlpoolHash ?: ByteArray(WHIRLPOOL_HASH_SIZE))
            }
        }
        if (containsSizes) {
            for (attr in groupSettings.values) {
                buf.writeInt(attr.sizes?.compressed ?: 0)
                buf.writeInt(attr.sizes?.uncompressed ?: 0)
            }
        }
        for (attr in groupSettings.values) {
            buf.writeInt(attr.version)
        }
        for (attr in groupSettings.values) {
            if (format == Format.VERSIONED_LARGE) {
                buf.writeUnsignedIntSmart(attr.fileSettings.size)
            } else {
                buf.writeShort(attr.fileSettings.size)
            }
        }
        for (attr in groupSettings.values) {
            var prevFileId = 0
            for (id in attr.fileSettings.keys) {
                val delta = abs(prevFileId - id)
                if (format == Format.VERSIONED_LARGE) {
                    buf.writeUnsignedIntSmart(delta)
                } else {
                    buf.writeShort(delta)
                }
                prevFileId = id
            }
        }
        if (containsNameHash) {
            groupSettings.values.flatMap { it.fileSettings.values }.forEach { buf.writeInt(it.nameHash ?: 0) }
        }
        return Js5Container(buf, compression, null) // settings containers don't have versions
    }

    /**
     * Different types of [Js5ArchiveSettings]
     *
     * @property opcode The opcode used to encode the format.
     */
    public enum class Format(public val opcode: Int) {
        UNVERSIONED(5), VERSIONED(6), VERSIONED_LARGE(7)
    }

    public companion object {
        private const val MASK_NAME_HASH = 0x01
        private const val MASK_WHIRLPOOL_HASH = 0x02
        private const val MASK_SIZES = 0x04
        private const val MASK_UNC_CRC = 0x08

        /**
         * Decodes the [Js5Container] into a [Js5ArchiveSettings].
         */
        public fun decode(container: Js5Container): Js5ArchiveSettings {
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
            val containsUnknownHash = flags and MASK_UNC_CRC != 0
            val groupCount = if (format == Format.VERSIONED_LARGE) {
                buf.readUnsignedIntSmart()
            } else buf.readUnsignedShort()
            val groupIds = IntArray(groupCount)
            var groupAccumulator = 0
            for (archiveIndex in groupIds.indices) {
                val delta = if (format == Format.VERSIONED_LARGE) buf.readUnsignedIntSmart() else buf.readUnsignedShort()
                groupAccumulator += delta
                groupIds[archiveIndex] = groupAccumulator
            }
            val nameHashes = if (containsNameHash) IntArray(groupCount) {
                buf.readInt()
            } else null
            val compressedCrcs = IntArray(groupCount) { buf.readInt() }
            val uncompressedCrcs = if (containsUnknownHash) IntArray(groupCount) {
                buf.readInt()
            } else null
            val whirlpoolHashes = if (containsWpHash) Array(groupCount) {
                ByteArray(WHIRLPOOL_HASH_SIZE) { buf.readByte() }
            } else null
            val sizes = if (containsSizes) Array(groupCount) {
                Js5Container.Size(compressed = buf.readInt(), uncompressed = buf.readInt())
            } else null
            val versions = Array(groupCount) { buf.readInt() }
            val fileIds = Array(groupCount) {
                IntArray(if (format == Format.VERSIONED_LARGE) buf.readUnsignedIntSmart() else buf.readUnsignedShort())
            }
            for (group in fileIds) {
                var fileIdAccumulator = 0
                for (fileIndex in group.indices) {
                    // difference with previous id
                    val delta = if (format == Format.VERSIONED_LARGE) {
                        buf.readUnsignedIntSmart()
                    } else buf.readUnsignedShort()
                    fileIdAccumulator += delta
                    group[fileIndex] = fileIdAccumulator
                }
            }
            val groupFileNameHashes = if (containsNameHash) {
                Array(groupCount) {
                    IntArray(fileIds[it].size) {
                        buf.readInt()
                    }
                }
            } else null

            val groupSettings = mutableMapOf<Int, Js5GroupSettings>()
            for (groupIndex in groupIds.indices) {
                val fileSettings = mutableMapOf<Int, Js5FileSettings>()
                for (fileIndex in fileIds[groupIndex].indices) {
                    fileSettings[fileIds[groupIndex][fileIndex]] = Js5FileSettings(
                        fileIds[groupIndex][fileIndex],
                        groupFileNameHashes?.get(groupIndex)?.get(fileIndex)
                    )
                }
                groupSettings[groupIds[groupIndex]] = Js5GroupSettings(
                    groupIds[groupIndex],
                    versions[groupIndex],
                    compressedCrcs[groupIndex],
                    fileSettings,
                    nameHashes?.get(groupIndex),
                    uncompressedCrcs?.get(groupIndex),
                    whirlpoolHashes?.get(groupIndex),
                    sizes?.get(groupIndex)

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
 * @property version (Optional) The version of the [Js5ArchiveSettings].
 * @property fileCount (Optional) The amount of [Js5File]s in the archive.
 * @property uncompressedSize (Optional) The size of the sum of all [Js5GroupData] data uncompressed.
 * @property whirlpoolDigest (Optional) The whirlpool digest of this archive.
 */
public data class Js5ArchiveValidator(
    var crc: Int,
    var version: Int?,
    var whirlpoolDigest: ByteArray?,
    var fileCount: Int?,
    var uncompressedSize: Int?
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

    internal companion object {
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


