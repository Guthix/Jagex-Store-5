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
import java.io.IOException
import java.math.BigInteger

/**
 * A readable and writeable [Js5Cache].
 *
 * Every [Js5Cache] needs to have a [Js5ContainerReader] and a [Js5ContainerWriter] or a [Js5ContainerReaderWriter].
 * The [Js5ContainerReader] is where all read operations are done and the [Js5ContainerWriter] is where all write
 * operations are done. Every cache has archives paired with settings. When creating a [Js5Cache] object all
 * [Js5ArchiveSettings] are loading from cache into [archiveSettings].
 *
 * @property rw The container read writer.
 * @property archiveSettings The [Js5ArchiveSettings].
 */
class Js5Cache(private val store: Js5DiskStore) : AutoCloseable {
    val archiveCount get() = store.archiveCount

    fun readArchive(archiveId: Int, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Archive {
        val data = store.read(store.masterIndex, archiveId)
        if(data == Unpooled.EMPTY_BUFFER) throw IOException(
            "Settings for archive $archiveId do not exist."
        )
        val container = Js5Container.decode(data, xteaKey)
        val archiveSettings = Js5ArchiveSettings.decode(container)
        val archiveIndexFile = store.openIdxFile(archiveId)
        return Js5Archive.create(store, archiveIndexFile, archiveSettings, container.xteaKey, container.compression)
    }

    fun addArchive(
        version: Int? = null,
        containsNameHash: Boolean = false,
        containsWpHash: Boolean = false,
        containsSizes: Boolean = false,
        containsUnknownHash: Boolean = false,
        xteaKey: IntArray = XTEA_ZERO_KEY,
        compression: Js5Compression = Uncompressed()
    ) =  Js5Archive(store, store.createIdxFile(), version, containsNameHash, containsWpHash,
            containsSizes, containsUnknownHash, xteaKey, compression
    )

    fun generateChecksum(xteaKeys: Map<Int, IntArray>): Js5CacheChecksum  {
        val archiveCount = store.archiveCount
        val archiveChecksums = mutableListOf<Js5ArchiveChecksum>()
        for(archiveIndex in 0 until archiveCount) {
            val data = store.read(store.masterIndex, archiveIndex)
            if(data == Unpooled.EMPTY_BUFFER) continue
            val settings = Js5ArchiveSettings.decode(
                Js5Container.decode(data, xteaKeys.getOrElse(archiveIndex) { XTEA_ZERO_KEY })
            )
            val uncompressedSize = settings.groupSettings.values.sumBy {
                it.sizes?.uncompressed ?: 0
            }
            archiveChecksums.add(Js5ArchiveChecksum(
                data.crc(), settings.version, settings.groupSettings.size, uncompressedSize, data.whirlPoolHash()
            ))
        }
        return Js5CacheChecksum(archiveChecksums.toTypedArray())
    }

    override fun close() =  store.close()
}

/**
 * Contains meta-daa for calculating the checksum of a [Js5Cache]. Cache checksums can optionally contain a whirlpool
 * hash which can optionally be encrypted using RSA.
 *
 * @property archiveChecksums The checksum data for each archive.
 */
data class Js5CacheChecksum(val archiveChecksums: Array<Js5ArchiveChecksum>) {
    val containsWhirlpool get() = archiveChecksums.all { it.whirlpoolDigest != null }

    val newFormat get() = archiveChecksums.all { it.fileCount != null } &&
            archiveChecksums.all { it.uncompressedSize != null }

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
            val digest = buf.whirlPoolHash(1, buf.writerIndex() - 1)
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
                val calcDigest = buf.whirlPoolHash(1, buf.readerIndex() - 1)
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
