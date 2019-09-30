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

import io.guthix.cache.js5.container.*
import io.guthix.cache.js5.container.disk.Js5DiskStore
import io.guthix.cache.js5.util.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.io.IOException
import java.math.BigInteger
import kotlin.IllegalArgumentException

private val logger = KotlinLogging.logger { }

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
class Js5Cache private constructor(
    private val reader: Js5ContainerReader,
    private val writer: Js5ContainerWriter,
    private val archiveSettings: MutableMap<Int, Js5ArchiveSettings>
) : AutoCloseable {
    val archiveCount get() = archiveSettings.size

    fun addArchive(
        version: Int? = null,
        containsNameHash: Boolean = false,
        containsWpHash: Boolean = false,
        containsSizes: Boolean = false,
        containsUnknownHash: Boolean = false,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        compression: Js5Compression = Uncompressed()
    ) {
        val archiveId = archiveSettings.size
        logger.debug { "Adding empty archive with id $archiveId" }
        archiveSettings[archiveId] = Js5ArchiveSettings(version, mutableMapOf(), containsNameHash, containsWpHash,
            containsSizes, containsUnknownHash, xteaKey, compression
        )
    }

    /**
     * Reads an archive from the cache by id.
     *
     * @param archiveId The id of the archive.
     * @param xteaKeys The xtea keys for decrypting the [Js5GroupData]s in the archive.
     */
    fun readArchive(archiveId: Int, xteaKeys: Map<Int, IntArray> = emptyMap()): Js5Archive {
        val archiveSettings = getArchiveSettings(archiveId)
        val groups = mutableMapOf<Int, Js5Group>()
        archiveSettings.js5GroupSettings.forEach { (groupId, groupSettings) ->
            groups[groupId] = readGroup(archiveId, groupSettings, xteaKeys.getOrElse(groupId) { XTEA_ZERO_KEY })
        }
        return Js5Archive(archiveId, groups, archiveSettings.version)
    }

    /**
     * Reads a [Js5GroupData] from the cache by group id.
     *
     * @param archiveId The archive to read from.
     * @param groupId The gropu to read from.
     * @param xteaKey The (Optional) XTEA key to decrypt the group container.
     */
    fun readGroup(archiveId: Int, groupId: Int, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Group {
        val settings = getArchiveSettings(archiveId).js5GroupSettings[groupId]
            ?: throw IllegalArgumentException("Unable to read group $groupId because it does not exist.")
        return readGroup(archiveId, settings, xteaKey)
    }

    /**
     * Reads a [Js5GroupData] from the cache by group name.
     *
     * @param archiveId The archive to read from.
     * @param groupName The name of the group to read.
     * @param xteaKey The (Optional) XTEA key to decrypt the group container.
     */
    fun readGroup(archiveId: Int, groupName: String, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Group {
        val nameHash = groupName.hashCode()
        val settings =  getArchiveSettings(archiveId).js5GroupSettings.values.firstOrNull {
            it.nameHash == nameHash
        } ?: throw IllegalArgumentException("Unable to read group `$groupName` because it does not exist.")
        return readGroup(archiveId, settings, xteaKey)
    }

    private fun readGroup(archiveId: Int, settings: Js5GroupSettings, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Group {
        val groupData = Js5GroupData.decode(
            Js5Container.decode(reader.read(archiveId, settings.id), xteaKey), settings.fileSettings.size
        )
        val group = Js5Group.create(groupData, settings)
        logger.info("Reading group ${settings.id} from archive $archiveId")
        return group
    }

    fun writeGroup(archiveId: Int, group: Js5Group) {
        val archiveSettings = archiveSettings.getOrElse(archiveId) {
            throw IllegalArgumentException("Unable to write to archive $archiveId because settings do not exist.")
        }
        val data = group.groupData.encode().encode()
        val compressedSize = writeGroupData(archiveId, group.id, data)
        val uncompressedSize = group.groupData.fileData.sumBy { it.writerIndex() }
        group.crc = data.crc()
        if(archiveSettings.containsWpHash) group.whirlpoolHash = data.array().whirlPoolHash()
        if(archiveSettings.containsSizes) group.groupSettings.sizes = Js5Container.Size(compressedSize, uncompressedSize)
        writeGroupSettings(archiveId, archiveSettings, group.groupSettings)
    }

    /**
     * Writes the group data for a [Js5GroupData].
     *
     * @param archiveId The id of the archive to write to.
     * @param groupData The [Js5GroupData] to write.
     */
    private fun writeGroupData(archiveId: Int, groupId: Int, data: ByteBuf): Int {
        val compressedSize = data.readableBytes()
        writer.write(archiveId, groupId, data)
        return compressedSize
    }

    /**
     * Writes the [Js5GroupSettings] for a [Js5GroupData].
     *
     * @param archiveId The id of the archive to write to.
     * @param group The [Js5GroupData] to write.
     */
    private fun writeGroupSettings(archiveId: Int, archiveSettings: Js5ArchiveSettings, group: Js5GroupSettings) {
        archiveSettings.js5GroupSettings[group.id] = group
        writer.write(Js5DiskStore.MASTER_INDEX, archiveId, archiveSettings.encode().encode())
    }

    private fun getArchiveSettings(archiveId: Int) = archiveSettings.getOrElse(archiveId) {
        throw IllegalArgumentException("Unable to read from archive $archiveId because it does not exist.")
    }

    /**
     * Generates the [Js5CacheChecksum] of this cache.
     */
    fun generateChecksum(): Js5CacheChecksum  = Js5CacheChecksum(
        archiveSettings.values.map { it.calculateChecksum() }.toTypedArray()
    )

    override fun close() {
        reader.close()
        writer.close()
    }

    companion object {
        fun open(rw: Js5ContainerReaderWriter, xteas: Array<IntArray> = arrayOf()) = open(rw, rw, xteas)

        fun open(r: Js5ContainerReader, w: Js5ContainerWriter, xteas: Array<IntArray> = arrayOf()): Js5Cache {
            require(xteas.isEmpty() || xteas.size == r.archiveCount)
            val settings = mutableMapOf<Int, Js5ArchiveSettings>()
            for(archiveId in 0 until r.archiveCount) {
                val data = r.read(Js5DiskStore.MASTER_INDEX, archiveId)
                if(data != Unpooled.EMPTY_BUFFER) {
                    settings[archiveId] = Js5ArchiveSettings.decode(
                        Js5Container.decode(data, xteas.getOrElse(archiveId) { XTEA_ZERO_KEY })
                    )
                }
            }
            return Js5Cache(r, w, settings)
        }
    }
}

/**
 * Contains meta-daa for calculating the checksum of a [Js5Cache]. Cache checksums can optionally contain a whirlpool
 * hash which can optionally be encrypted using RSA.
 *
 * @property archiveChecksums The checksum data for each archive.
 */
data class Js5CacheChecksum(val archiveChecksums: Array<Js5ArchiveChecksum>) {
    val containsWhirlpool get() = archiveChecksums.all { it.whirlpoolDigest != null }

    /**
     * Encodes the [Js5CacheChecksum]. The encoding can optionally contain a (encrypted) whirlpool hash.
     *
     * @param whirlpool Whether to add the whirlpool hash to the checksum.
     * @param mod Modulus to (optionally) encrypt the whirlpool hash using RSA.
     * @param pubKey The public key to (optionally) encrypt the whirlpool hash using RSA.
     */
    fun encode(mod: BigInteger? = null, pubKey: BigInteger? = null, newFormat: Boolean = false): ByteBuf {
        archiveChecksums.all { it.whirlpoolDigest != null }
        val buf = Unpooled.buffer(if(containsWhirlpool)
            WP_ENCODED_SIZE + Js5ArchiveChecksum.WP_ENCODED_SIZE * archiveChecksums.size
        else
            Js5ArchiveChecksum.ENCODED_SIZE * archiveChecksums.size
        )
        if(containsWhirlpool) buf.writeByte(archiveChecksums.size)
        for(archiveChecksum in archiveChecksums) {
            buf.writeInt(archiveChecksum.crc)
            buf.writeInt(archiveChecksum.version ?: 0)
            if(containsWhirlpool) {
                if(newFormat) {
                    buf.writeInt(archiveChecksum.fileCount ?: 0)
                    buf.writeInt(archiveChecksum.uncompressedSize ?: 0)
                }
                buf.writeBytes(archiveChecksum.whirlpoolDigest)
            }
        }
        if(containsWhirlpool) {
            val digest = buf.array().sliceArray(1 until buf.writerIndex()).whirlPoolHash()
            val encDigest = if(mod != null && pubKey != null) rsaCrypt(digest, mod, pubKey) else digest
            buf.writeBytes(encDigest)
        }
        return buf
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5CacheChecksum
        if (!archiveChecksums.contentEquals(other.archiveChecksums)) return false
        return true
    }

    override fun hashCode(): Int {
        return archiveChecksums.contentHashCode()
    }

    companion object {
        const val WP_ENCODED_SIZE = Byte.SIZE_BYTES + WHIRLPOOL_HASH_SIZE

        /**
         * Decodes the [Js5CacheChecksum]. The encoding can optionally contain a (encrypted) whirlpool hash.
         *
         * @param buf The buf to decode
         * @param whirlpool Whether to decode the whirlpool hash.
         * @param mod Modulus for decrypting the whirlpool hash using RSA.
         * @param privateKey The public key to (optionally) encrypt the whirlpool hash.
         */
        fun decode(
            buf: ByteBuf,
            whirlpool: Boolean = false,
            mod: BigInteger? = null,
            privateKey: BigInteger? = null,
            newFormat: Boolean = false
        ): Js5CacheChecksum {
            val archiveCount = if (whirlpool) {
                buf.readUnsignedByte().toInt()
            } else {
                buf.writerIndex() / Js5ArchiveChecksum.ENCODED_SIZE
            }
            val archiveChecksums = Array(archiveCount) {
                val crc = buf.readInt()
                val version = buf.readInt()
                val fileCount = if (whirlpool && newFormat) buf.readInt() else null
                val indexFileSize = if (whirlpool && newFormat) buf.readInt() else null
                val whirlPoolDigest = if (whirlpool) {
                    val digest = ByteArray(WHIRLPOOL_HASH_SIZE)
                    buf.readBytes(digest)
                    digest
                } else null
                Js5ArchiveChecksum(crc, version, fileCount, indexFileSize, whirlPoolDigest)
            }
            if (whirlpool) {
                val calcDigest = buf.array().sliceArray(1 until buf.readerIndex()).whirlPoolHash()
                val readDigest =  buf.array().sliceArray(buf.readerIndex() until buf.writerIndex())
                val decReadDigest= if (mod != null && privateKey != null) {
                    rsaCrypt(readDigest, mod, privateKey)
                } else {
                    readDigest
                }
                if (!decReadDigest!!.contentEquals(calcDigest)) throw IOException("Whirlpool digest does not match,  " +
                        "calculated ${calcDigest.contentToString()} read ${decReadDigest.contentToString()}."
                )
            }
            return Js5CacheChecksum(archiveChecksums)
        }
    }
}
