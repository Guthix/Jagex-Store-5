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
import io.guthix.cache.js5.container.disk.IdxFile
import io.guthix.cache.js5.util.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.IOException
import java.math.BigInteger

/**
 * A modifiable Jagex Store 5 cache. The [Js5Cache] serves as a wrapper around the [Js5DiskStore] to provide domain
 * encoded data access to game assets.
 *
 * @property store The [Js5DiskStore] to modify.
 */
class Js5Cache(private val store: Js5DiskStore) : AutoCloseable {
    /**
     * The amount of achives in this [Js5Cache].
     */
    val archiveCount get() = store.archiveCount

    /**
     * Reads an archive from the [Js5Cache].
     *
     * @param archiveId The archive id to read.
     * @param xteaKey The XTEA key to decrypt the [Js5ArchiveSettings] of this [Js5Archive].
     */
    fun readArchive(archiveId: Int, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Archive {
        val data = store.read(store.masterIdxFile, archiveId)
        if(data == Unpooled.EMPTY_BUFFER) throw IOException(
            "Settings for archive $archiveId do not exist."
        )
        val container = Js5Container.decode(data, xteaKey)
        val archiveSettings = Js5ArchiveSettings.decode(container)
        val archiveIndexFile = store.openArchiveIdxFile(archiveId)
        return Js5Archive.create(archiveIndexFile, store, archiveSettings, container.xteaKey, container.compression)
    }

    /**
     * Adds an archive to the [Js5Cache]. The index of this archive will be the next unused [IdxFile].
     *
     * @param version The version of this [Js5Archive].
     * @param containsNameHash Whether this archive contains names.
     * @param containsWpHash Whether this archive contains whirlpool hashes.
     * @param containsSizes Whether this archive contains the [Js5Container.Size] in the [Js5ArchiveSettings].
     * @param containsUnknownHash Whether this archive contains an yet unknown hash.
     * @param xteaKey The XTEA key to decrypt the [Js5ArchiveSettings].
     * @param compression The [Js5Compression] used to store the [Js5ArchiveSettings].
     */
    fun addArchive(
            version: Int? = null,
            containsNameHash: Boolean = false,
            containsWpHash: Boolean = false,
            containsSizes: Boolean = false,
            containsUnknownHash: Boolean = false,
            xteaKey: IntArray = XTEA_ZERO_KEY,
            compression: Js5Compression = Uncompressed()
    ) = Js5Archive(version, containsNameHash, containsWpHash, containsSizes, containsUnknownHash, xteaKey, compression,
        mutableMapOf(), store.createArchiveIdxFile(), store
    )

    /**
     * Generates the [Js5CacheValidator] for this [Js5Cache].
     *
     * @param xteaKeys The XTEA keys to decrypt the [Js5ArchiveSettings].
     */
    fun generateValidator(xteaKeys: Map<Int, IntArray>): Js5CacheValidator  {
        val archiveCount = store.archiveCount
        val archiveChecksums = mutableListOf<Js5ArchiveValidator>()
        for(archiveIndex in 0 until archiveCount) {
            val data = store.read(store.masterIdxFile, archiveIndex)
            if(data == Unpooled.EMPTY_BUFFER) continue
            val settings = Js5ArchiveSettings.decode(
                Js5Container.decode(data, xteaKeys.getOrElse(archiveIndex) { XTEA_ZERO_KEY })
            )
            val uncompressedSize = settings.groupSettings.values.sumBy {
                it.sizes?.uncompressed ?: 0
            }
            archiveChecksums.add(Js5ArchiveValidator(
                data.crc(), settings.version ?: 0, settings.groupSettings.size, uncompressedSize,
                data.whirlPoolHash()
            ))
        }
        return Js5CacheValidator(archiveChecksums.toTypedArray())
    }

    override fun close() =  store.close()
}

/**
 * Contains meta-daa for validating data from a [Js5Cache]. There are multiple versions of the validator encoding. The
 * validator can also be encrypted with an RSA encryption.
 *
 * @property archiveValidators The validators for each [Js5Archive].
 */
data class Js5CacheValidator(val archiveValidators: Array<Js5ArchiveValidator>) {
    val containsWhirlpool get() = archiveValidators.all { it.whirlpoolDigest != null }

    val newFormat get() = archiveValidators.all { it.fileCount != null } &&
            archiveValidators.all { it.uncompressedSize != null }

    /**
     * Encodes the [Js5CacheValidator]. The encoding can optionally contain a (encrypted) whirlpool hash.
     *
     * @param mod Modulus to (optionally) encrypt the whirlpool hash using RSA.
     * @param pubKey The public key to (optionally) encrypt the whirlpool hash using RSA.
     * @param newFormat Whether to use the new format, only used when the validator also contains whirlpool hashes.
     */
    fun encode(mod: BigInteger? = null, pubKey: BigInteger? = null, newFormat: Boolean = false): ByteBuf {
        val buf = when {
            containsWhirlpool && newFormat -> Unpooled.buffer(
                WP_ENCODED_SIZE + Js5ArchiveValidator.ENCODED_SIZE_WP_NEW * archiveValidators.size
            )
            containsWhirlpool && !newFormat -> Unpooled.buffer(
                WP_ENCODED_SIZE + Js5ArchiveValidator.WP_ENCODED_SIZE * archiveValidators.size

            )
            else -> Unpooled.buffer(Js5ArchiveValidator.ENCODED_SIZE * archiveValidators.size)
        }
        if(containsWhirlpool) buf.writeByte(archiveValidators.size)
        for(archiveChecksum in archiveValidators) {
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
        other as Js5CacheValidator
        if (!archiveValidators.contentEquals(other.archiveValidators)) return false
        return true
    }

    override fun hashCode(): Int {
        return archiveValidators.contentHashCode()
    }

    companion object {
        /**
         * Byte size required when the validator contains whirlpool hashes.
         */
        const val WP_ENCODED_SIZE = Byte.SIZE_BYTES + WHIRLPOOL_HASH_SIZE

        /**
         * Decodes the [Js5CacheValidator]. The encoding can optionally contain a (encrypted) whirlpool hash.
         *
         * @param buf The buf to decode
         * @param whirlpool Whether to decode the whirlpool hash.
         * @param mod Modulus for decrypting the whirlpool hash using RSA.
         * @param privateKey The public key to (optionally) encrypt the whirlpool hash.
         * @param newFormat Whether to decode using the new format.
         */
        fun decode(
            buf: ByteBuf,
            whirlpool: Boolean = false,
            mod: BigInteger? = null,
            privateKey: BigInteger? = null,
            newFormat: Boolean = false
        ): Js5CacheValidator {
            val archiveCount = if (whirlpool) {
                buf.readUnsignedByte().toInt()
            } else {
                buf.writerIndex() / Js5ArchiveValidator.ENCODED_SIZE
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
                Js5ArchiveValidator(crc, version, fileCount, indexFileSize, whirlPoolDigest)
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
            return Js5CacheValidator(archiveChecksums)
        }
    }
}
