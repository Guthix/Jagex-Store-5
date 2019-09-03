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

import io.guthix.cache.js5.container.Js5ContainerReader
import io.guthix.cache.js5.container.Js5ContainerWriter
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.Js5ContainerReaderWriter
import io.guthix.cache.js5.container.filesystem.Js5FileSystem
import io.guthix.cache.js5.io.uByte
import io.guthix.cache.js5.util.*
import mu.KotlinLogging
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

/**
 * A readable and writeable [Js5Cache].
 *
 * Every [Js5Cache] needs to have a [Js5ContainerReader] and a [Js5ContainerWriter] or a [Js5ContainerReaderWriter].
 * The [Js5ContainerReader] is where all read operations are done and the [Js5ContainerWriter] is where all write
 * operations are done. Every cache has archives paired with settings. When creating a [Js5Cache] object all
 * [Js5ArchiveSettings] are loading from cache into [archiveSettings].
 *
 * @property reader The container reader.
 * @property writer The container writer.
 * @property settingsXtea (Optional) XTEA keys for decrypting the [Js5ArchiveSettings].
 */
open class Js5Cache(
    private val reader: Js5ContainerReader,
    private val writer: Js5ContainerWriter? = null,
    private val settingsXtea: MutableMap<Int, IntArray> = mutableMapOf()
) : AutoCloseable {
    constructor(
        readerWriter: Js5ContainerReaderWriter,
        settingsXtea: MutableMap<Int, IntArray> = mutableMapOf()
    ) : this(reader = readerWriter,  writer = readerWriter, settingsXtea = settingsXtea)

    /**
     * The [Js5ArchiveSettings] for all the archives in this [Js5Cache].
     */
    val archiveSettings = MutableList(reader.archiveCount) {
        Js5ArchiveSettings.decode(
            Js5Container.decode(
                reader.read(
                    Js5FileSystem.MASTER_INDEX,
                    it
                ),
                settingsXtea[it] ?: XTEA_ZERO_KEY
            )
        )
    }

    init {
        logger.info("Loaded cache with ${archiveSettings.size} archives")
    }

    /**
     * Reads raw data from the [reader].
     *
     * @param indexId The index id to read. For reading groups the index id is equivalent to the archive id and for the
     * settings the index id is equivalent to the [Js5FileSystem.MASTER_INDEX].
     * @param containerId The container id to read which is equivalent to the archive id for reading
     * [Js5ArchiveSettings] data and the group id for reading [Js5Group] data.
     */
    open fun readData(indexId: Int, containerId: Int): ByteArray = reader.read(indexId, containerId)

    /**
     * Reads a [Js5Group] from the cache by group id.
     *
     * @param archiveId The archive to read from.
     * @param groupId The gropu to read from.
     * @param xteaKey The (Optional) XTEA key to decrypt the group container.
     */
    open fun readGroup(
        archiveId: Int,
        groupId: Int,
        xteaKey: IntArray = XTEA_ZERO_KEY
    ): Js5Group {
        val archiveSettings = archiveSettings[archiveId]
        val groupSettings = archiveSettings.js5GroupSettings[groupId] ?: throw IOException("Js5Group does not exist.")
        val groupContainer = Js5Container.decode(readData(archiveId, groupId), xteaKey)
        logger.info("Reading group $groupId from archive $archiveId")
        return Js5Group.decode(groupContainer, groupSettings)
    }

    /**
     * Reads a [Js5Group] from the cache by group name.
     *
     * @param archiveId The archive to read from.
     * @param groupName The name of the group to read.
     * @param xteaKey The (Optional) XTEA key to decrypt the group container.
     */
    open fun readGroup(
        archiveId: Int,
        groupName: String,
        xteaKey: IntArray = XTEA_ZERO_KEY
    ): Js5Group {
        val archiveSettings = archiveSettings[archiveId]
        val nameHash = groupName.hashCode()
        val groupSettings =  archiveSettings.js5GroupSettings.values.first { it.nameHash == nameHash }
        logger.info("Reading group ${groupSettings.id} from archive $archiveId")
        val groupContainer = Js5Container.decode(readData(archiveId, groupSettings.id), xteaKey)
        return Js5Group.decode(groupContainer, groupSettings)
    }

    /**
     * Reads an archive from the cache by id.
     *
     * @param archiveId The id of the archive.
     * @param xteaKeys The xtea keys for decrypting the [Js5Group]s in the archive.
     */
    open fun readArchive(
        archiveId: Int,
        xteaKeys: Map<Int, IntArray> = emptyMap()
    ): Map<Int, Js5Group> {
        val archiveSettings = archiveSettings[archiveId]
        val groups = mutableMapOf<Int, Js5Group>()
        archiveSettings.js5GroupSettings.forEach { (groupId, groupSettings) ->
            val xtea = xteaKeys[groupId] ?: XTEA_ZERO_KEY
            logger.info("Reading group ${groupSettings.id} from archive $archiveId")
            val groupContainer = Js5Container.decode(readData(archiveId, groupId), xtea)
            groups[groupId] = Js5Group.decode(groupContainer, groupSettings)
        }
        return groups
    }

    /**
     * Writes a [Js5Group] to this cache.
     *
     * @param archiveId The id of the archive to write to.
     * @param archiveVersion (Optional) The version of the archive.
     * @param group The [Js5Group] to write.
     * @param groupChunkCount (Optional) The amount of chunks to use for encoding the [Js5Group].
     * @param groupXteaKey (Optional) The XTEA key for encoding the [Js5Container] for the [Js5Group].
     * @param groupSettingsXteaKey (Optional) The XTEA key for encoding the [Js5Container] for the [Js5GroupSettings].
     * @param groupCompression (Optional) The compression for encoding the [Js5Container] for the [Js5Group].
     * @param groupSettingsCompression (Optional) The compression for encoding the [Js5Container] for the
     * [Js5GroupSettings].
     */
    open fun writeGroup(
        archiveId: Int,
        archiveVersion: Int? = null,
        group: Js5Group,
        groupChunkCount: Int = 1,
        groupXteaKey: IntArray = XTEA_ZERO_KEY,
        groupSettingsXteaKey: IntArray = XTEA_ZERO_KEY,
        groupCompression: Js5Compression = Js5Compression.NONE,
        groupSettingsCompression: Js5Compression = Js5Compression.NONE
    ) {
        if(writer == null) throw IllegalCallerException("There is no writer specified for this cache.")
        if(archiveId > writer.archiveCount) throw IOException(
            "Can not create archive with id $archiveId expected: ${writer.archiveCount}."
        )
        logger.info("Writing group ${group.id} from archive $archiveId")
        val compressedSize = writeGroupData(
            archiveId, group, groupChunkCount, groupXteaKey, groupCompression
        )
        if(group.sizes == null) {
            group.sizes = Js5GroupSettings.Size(compressedSize, group.files.values.sumBy { it.data.size })
        }
        writeGroupSettings(
            archiveId,
            archiveVersion,
            group,
            groupSettingsXteaKey,
            groupSettingsCompression
        )
    }

    /**
     * Writes the group data for a [Js5Group].
     *
     * @param archiveId The id of the archive to write to.
     * @param group The [Js5Group] to write.
     * @param groupChunkCount (Optional) The amount of chunks to use for encoding.
     * @param xteaKey (Optional) The XTEA key for encoding the [Js5Container].
     * @param compression (Optional) The compression for encoding the [Js5Container].
     */
    private fun writeGroupData(
        archiveId: Int,
        group: Js5Group,
        groupChunkCount: Int = 1,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        compression: Js5Compression = Js5Compression.NONE
    ): Int {
        logger.info("Writing group data for group ${group.id} from archive $archiveId")
        val groupContainer = group.encode(groupChunkCount)
        val data = groupContainer.encode(compression, xteaKey)
        writer!!.write(archiveId, group.id, data)
        return data.size
    }

    /**
     * Writes the [Js5GroupSettings] for a [Js5Group].
     *
     * @param archiveId The id of the archive to write to.
     * @param archiveVersion (Optional) The version of the archive.
     * @param group The [Js5Group] to write.
     * @param xteaKey (Optional) The XTEA key for encoding the [Js5Container].
     * @param compression (Optional) The compression for encoding the [Js5Container].
     */
    private fun writeGroupSettings(
        archiveId: Int,
        archiveVersion: Int? = null,
        group: Js5Group,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        compression: Js5Compression = Js5Compression.NONE
    ) {
        logger.info("Writing group settings for group ${group.id} from archive $archiveId")
        val archiveSettings = if(archiveId == archiveSettings.size) {
            val settings = Js5ArchiveSettings(0, mutableMapOf())
            archiveSettings.add(archiveId, settings)
            settings
        } else {
            archiveSettings[archiveId]
        }
        val fileSettings = mutableMapOf<Int, Js5FileSettings>()
        group.files.forEach { (id, file) ->
            fileSettings[id] = Js5FileSettings(id, file.nameHash)
        }
        if(!xteaKey.contentEquals(XTEA_ZERO_KEY)) settingsXtea[archiveId] = xteaKey
        archiveSettings.version = archiveVersion
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
        writer!!.write(
            Js5FileSystem.MASTER_INDEX,
            archiveId,
            archiveSettings.encode().encode(compression, xteaKey)
        )
    }

    override fun close() {
        reader.close()
        writer?.close()
    }

    /**
     * Generates the [CheckSum] of this cache.
     */
    fun generateChecksum(): CheckSum {
        logger.info("Generating cache checksum")
        return CheckSum(
            Array(archiveSettings.size) { archiveId ->
                val archiveSettings = archiveSettings[archiveId]
                val settingsData = reader.read(Js5FileSystem.MASTER_INDEX, archiveId)
                ArchiveChecksum(
                    settingsData.crc(),
                    archiveSettings.version,
                    archiveSettings.js5GroupSettings.size,
                    archiveSettings.js5GroupSettings.values
                        .sumBy { if(it.sizes?.compressed != null) it.sizes.uncompressed else 0 },
                    settingsData.whirlPoolHash()
                )
            }
        )
    }

    /**
     * Contains meta-daa for calculating the checksum of a [Js5Cache]. Cache checksums can optionally contain a whirlpool
     * hash which can optionally be encrypted using RSA.
     *
     * @property archiveChecksums The checksum data for each archive.
     */
    data class CheckSum(val archiveChecksums: Array<ArchiveChecksum>) {
        /**
         * Encodes the [CheckSum]. The encoding can optionally contain a (encrypted) whirlpool hash.
         *
         * @param whirlpool Whether to add the whirlpool hash to the checksum.
         * @param mod Modulus to (optionally) encrypt the whirlpool hash using RSA.
         * @param pubKey The public key to (optionally) encrypt the whirlpool hash using RSA.
         */
        fun encode(whirlpool: Boolean = false, mod: BigInteger? = null, pubKey: BigInteger? = null): ByteArray {
            val buffer = ByteBuffer.allocate(if(whirlpool)
                WP_ENCODED_SIZE + ArchiveChecksum.WP_ENCODED_SIZE * archiveChecksums.size
            else
                ArchiveChecksum.ENCODED_SIZE * archiveChecksums.size
            )
            if(whirlpool) buffer.put(archiveChecksums.size.toByte())
            for(indexFileChecksum in archiveChecksums) {
                buffer.putInt(indexFileChecksum.crc)
                buffer.putInt(indexFileChecksum.version ?: 0)
                if(whirlpool) {
                    buffer.putInt(indexFileChecksum.fileCount)
                    buffer.putInt(indexFileChecksum.size)
                    buffer.put(indexFileChecksum.whirlpoolDigest)
                }
            }
            if(whirlpool) {
                val hash = if(mod != null && pubKey != null) {
                    rsaCrypt(
                        buffer.array().sliceArray(1 until buffer.position()).whirlPoolHash(),
                        mod,
                        pubKey
                    )
                } else {
                    buffer.array().sliceArray(1 until buffer.position()).whirlPoolHash()
                }
                buffer.put(hash)
            }
            return buffer.array()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CheckSum
            if (!archiveChecksums.contentEquals(other.archiveChecksums)) return false
            return true
        }

        override fun hashCode(): Int {
            return archiveChecksums.contentHashCode()
        }

        companion object {
            const val WP_ENCODED_SIZE = WHIRLPOOL_HASH_SIZE + 1

            /**
             * Decodes the [CheckSum]. The encoding can optionally contain a (encrypted) whirlpool hash.
             *
             * @param data The data to decode
             * @param whirlpool Whether to decode the whirlpool hash.
             * @param mod Modulus for decrypting the whirlpool hash using RSA.
             * @param privateKey The public key to (optionally) encrypt the whirlpool hash.
             */
            fun decode(
                data: ByteArray,
                whirlpool: Boolean,
                mod: BigInteger?,
                privateKey: BigInteger?
            ): CheckSum {
                val buffer = ByteBuffer.wrap(data)
                val indexFileCount = if (whirlpool) {
                    buffer.uByte.toInt()
                } else {
                    buffer.limit() / ArchiveChecksum.ENCODED_SIZE
                }
                val indexFileEncodedSize = if(whirlpool) {
                    ArchiveChecksum.WP_ENCODED_SIZE * indexFileCount
                } else {
                    ArchiveChecksum.ENCODED_SIZE * indexFileCount
                }
                val indexFileEncodedStart = if (whirlpool) 1 else 0
                val calculatedDigest = buffer.array().sliceArray(
                    indexFileEncodedStart until indexFileEncodedStart + indexFileEncodedSize
                ).whirlPoolHash()
                val indexFileChecksums = Array(indexFileCount) {
                    val crc = buffer.int
                    val version = buffer.int
                    val fileCount = if (whirlpool) buffer.int else 0
                    val indexFileSize = if (whirlpool) buffer.int else 0
                    val whirlPoolDigest = if (whirlpool) {
                        val digest = ByteArray(WHIRLPOOL_HASH_SIZE)
                        buffer.get(digest)
                        digest
                    } else null
                    ArchiveChecksum(crc, version, fileCount, indexFileSize, whirlPoolDigest)
                }
                if (whirlpool) {
                    val hash= if (mod != null && privateKey != null) {
                        rsaCrypt(
                            buffer.array().sliceArray(
                                buffer.position() until buffer.position() + buffer.remaining()
                            ), mod, privateKey
                        )
                    } else {
                        buffer.array().sliceArray(buffer.position() until buffer.position() + buffer.remaining())
                    }
                    if (!hash!!.contentEquals(calculatedDigest)) throw IOException("Whirlpool digest does not match.")
                }
                return CheckSum(indexFileChecksums)
            }
        }
    }

    /**
     * The checksum meta-data for an archive.
     *
     * @property crc The [java.util.zip.CRC32] value of the [Js5ArchiveSettings] as an encoded [Js5Container].
     * @property version (Optional) he version of the [Js5ArchiveSettings].
     * @property fileCount The amount of [Js5Group.File] in the archive.
     * @property size The size of the sum of all [Js5Group] data uncompressed.
     * @property whirlpoolDigest (Optional) The whirlpool digest of this archive.
     */
    data class ArchiveChecksum(
        val crc: Int,
        val version: Int?,
        val fileCount: Int,
        val size: Int,
        val whirlpoolDigest: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ArchiveChecksum
            if (crc != other.crc) return false
            if (version != other.version) return false
            if (fileCount != other.fileCount) return false
            if (size != other.size) return false
            if (whirlpoolDigest != null) {
                if (other.whirlpoolDigest == null) return false
                if (!whirlpoolDigest.contentEquals(other.whirlpoolDigest)) return false
            } else if (other.whirlpoolDigest != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = crc
            result = 31 * result + (version ?: 0)
            result = 31 * result + fileCount
            result = 31 * result + size
            result = 31 * result + (whirlpoolDigest?.contentHashCode() ?: 0)
            return result
        }

        companion object {
            internal const val ENCODED_SIZE = 8
            internal const val WP_ENCODED_SIZE = ENCODED_SIZE + WHIRLPOOL_HASH_SIZE + 8
        }
    }
}
