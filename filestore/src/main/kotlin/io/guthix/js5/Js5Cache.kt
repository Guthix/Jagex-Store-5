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

import io.guthix.js5.container.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.IOException
import java.math.BigInteger

/**
 * A modifiable Jagex Store 5 cache. The [Js5Cache] serves as a wrapper around the [Js5Store] to provide domain
 * encoded data access to game assets.
 *
 * @property readStore The [Js5ReadStore] where all read operations are done.
 * @property writeStore The [Js5WriteStore] where all the write operations are done.
 */
public class Js5Cache(
    private val readStore: Js5ReadStore,
    private val writeStore: Js5WriteStore? = null
) : AutoCloseable {
    public constructor(store: Js5Store) : this(store, store)

    /**
     * The amount of archives in this [Js5Cache], taken from the [Js5ReadStore].
     */
    public val archiveCount: Int get() = readStore.archiveCount

    /**
     * Reads an archive from the [Js5Cache].
     *
     * @param archiveId The archive id to read.
     * @param xteaKey The XTEA key to decrypt the [Js5ArchiveSettings] of this [Js5Archive].
     */
    public fun readArchive(archiveId: Int, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Archive {
        val data = readStore.read(Js5Store.MASTER_INDEX, archiveId)
        if (data == Unpooled.EMPTY_BUFFER) throw IOException(
            "Settings for archive $archiveId do not exist."
        )
        val container = Js5Container.decode(data, xteaKey)
        val archiveSettings = Js5ArchiveSettings.decode(container)
        return Js5Archive.create(
            archiveId, archiveSettings, container.compression, readStore, writeStore
        )
    }

    public fun writeArchive(archive: Js5Archive, xteaKey: IntArray = XTEA_ZERO_KEY) {
        writeStore ?: error("Can't write archive because there is no write store provided.")
        writeStore.write(
            Js5Store.MASTER_INDEX,
            archive.id,
            Js5ArchiveSettings.from(archive).encode(archive.compression).encode(xteaKey)
        )
    }

    /**
     * Adds an archive to the [Js5Cache]. The index of this archive will be the next unused index id.
     *
     * @param version The version of this [Js5Archive].
     * @param containsNameHash Whether this archive contains names.
     * @param containsWpHash Whether this archive contains whirlpool hashes.
     * @param containsSizes Whether this archive contains the [Js5Container.Size] in the [Js5ArchiveSettings].
     * @param containsUnknownHash Whether this archive contains an yet unknown hash.
     * @param compression The [Js5Compression] used to store the [Js5ArchiveSettings].
     */
    public fun addArchive(
        version: Int? = null,
        containsNameHash: Boolean = false,
        containsWpHash: Boolean = false,
        containsSizes: Boolean = false,
        containsUnknownHash: Boolean = false,
        compression: Js5Compression = Uncompressed
    ): Js5Archive {
        writeStore ?: error("Can't add archive because there is no write store provided.")
        return Js5Archive(writeStore.archiveCount, version, containsNameHash, containsWpHash, containsSizes,
            containsUnknownHash, compression, sortedMapOf(), readStore, writeStore
        )
    }

    /**
     * Generates the [Js5CacheValidator] for this [Js5Cache].
     *
     * @param xteaKeys The XTEA keys to decrypt the [Js5ArchiveSettings].
     * @param includeWhirlpool Whether to include a whirlpool hash of the archive.
     * @param includeSizes Whether to include the uncompressed size and group count for every archive.
     */
    public fun generateValidator(
        includeWhirlpool: Boolean,
        includeSizes: Boolean,
        xteaKeys: Map<Int, IntArray> = emptyMap()
    ): Js5CacheValidator {
        val archiveChecksums = mutableListOf<Js5ArchiveValidator>()
        for (archiveIndex in 0 until archiveCount) {
            val data = readStore.read(Js5Store.MASTER_INDEX, archiveIndex)
            if (data == Unpooled.EMPTY_BUFFER) continue
            val settings = Js5ArchiveSettings.decode(
                Js5Container.decode(data.duplicate(), xteaKeys.getOrElse(archiveIndex) { XTEA_ZERO_KEY })
            )
            val whirlPool = if (includeWhirlpool) data.whirlPoolHash() else null
            val (groupCount, uncompressedSize) = if (includeSizes) {
                val uncompressedSize = settings.groupSettings.values.sumOf {
                    it.sizes?.uncompressed ?: 0
                }
                settings.groupSettings.size to uncompressedSize
            } else {
                null to null
            }
            archiveChecksums.add(Js5ArchiveValidator(
                data.crc(), settings.version ?: 0, whirlPool, groupCount, uncompressedSize)
            )
        }
        return Js5CacheValidator(archiveChecksums.toTypedArray())
    }

    override fun close() {
        readStore.close()
        writeStore?.close()
    }
}

/**
 * Contains meta-daa for validating data from a [Js5Cache]. There are multiple versions of the validator encoding. The
 * validator can also be encrypted with an RSA encryption.
 *
 * @property archiveValidators The validators for each [Js5Archive].
 */
public data class Js5CacheValidator(val archiveValidators: Array<Js5ArchiveValidator>) {
    public val containsWhirlpool: Boolean get() = archiveValidators.all { it.whirlpoolDigest != null }

    public val newFormat: Boolean
        get() = archiveValidators.all { it.fileCount != null }
            && archiveValidators.all { it.uncompressedSize != null }

    /**
     * Encodes the [Js5CacheValidator]. The encoding can optionally contain a (encrypted) whirlpool hash.
     *
     * @param mod Modulus to (optionally) encrypt the whirlpool hash using RSA.
     * @param pubKey The public key to (optionally) encrypt the whirlpool hash using RSA.
     */
    public fun encode(mod: BigInteger? = null, pubKey: BigInteger? = null): ByteBuf {
        val buf = when {
            containsWhirlpool && newFormat -> Unpooled.buffer(
                WP_ENCODED_SIZE + Js5ArchiveValidator.ENCODED_SIZE_WP_NEW * archiveValidators.size
            )
            containsWhirlpool && !newFormat -> Unpooled.buffer(
                WP_ENCODED_SIZE + Js5ArchiveValidator.WP_ENCODED_SIZE * archiveValidators.size

            )
            else -> Unpooled.buffer(Js5ArchiveValidator.ENCODED_SIZE * archiveValidators.size)
        }
        if (containsWhirlpool) buf.writeByte(archiveValidators.size)
        for ((crc, version, whirlpoolDigest, fileCount, uncompressedSize) in archiveValidators) {
            buf.writeInt(crc)
            buf.writeInt(version ?: 0)
            if (containsWhirlpool) {
                if (newFormat) {
                    buf.writeInt(fileCount ?: 0)
                    buf.writeInt(uncompressedSize ?: 0)
                }
                buf.writeBytes(whirlpoolDigest)
            }
        }
        if (containsWhirlpool) {
            val digest = buf.whirlPoolHash(1, buf.writerIndex() - 1)
            val encDigest = if (mod != null && pubKey != null) rsaCrypt(digest, mod, pubKey) else digest
            buf.writeBytes(encDigest)
        }
        return buf
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5CacheValidator
        return archiveValidators.contentEquals(other.archiveValidators)
    }

    override fun hashCode(): Int = archiveValidators.contentHashCode()

    public companion object {
        /**
         * Byte size required when the validator contains whirlpool hashes.
         */
        private const val WP_ENCODED_SIZE = Byte.SIZE_BYTES + WHIRLPOOL_HASH_SIZE

        /**
         * Decodes the [Js5CacheValidator]. The encoding can optionally contain a (encrypted) whirlpool hash.
         *
         * @param buf The buf to decode
         * @param whirlpoolIncluded Whether to decode the whirlpool hash.
         * @param mod Modulus for decrypting the whirlpool hash using RSA.
         * @param privateKey The public key to (optionally) encrypt the whirlpool hash.
         * @param sizeIncluded Whether to decode using the new format.
         */
        public fun decode(
            buf: ByteBuf,
            whirlpoolIncluded: Boolean,
            sizeIncluded: Boolean,
            mod: BigInteger? = null,
            privateKey: BigInteger? = null
        ): Js5CacheValidator {
            val archiveCount = if (whirlpoolIncluded) {
                buf.readUnsignedByte().toInt()
            } else {
                buf.writerIndex() / Js5ArchiveValidator.ENCODED_SIZE
            }
            val archiveChecksums = Array(archiveCount) {
                val crc = buf.readInt()
                val version = buf.readInt()
                val fileCount = if (whirlpoolIncluded && sizeIncluded) buf.readInt() else null
                val indexFileSize = if (whirlpoolIncluded && sizeIncluded) buf.readInt() else null
                val whirlPoolDigest = if (whirlpoolIncluded) {
                    val digest = ByteArray(WHIRLPOOL_HASH_SIZE)
                    buf.readBytes(digest)
                    digest
                } else null
                Js5ArchiveValidator(crc, version, whirlPoolDigest, fileCount, indexFileSize)
            }
            if (whirlpoolIncluded) {
                val calcDigest = buf.whirlPoolHash(1, buf.readerIndex() - 1)
                val readDigest = buf.array().sliceArray(buf.readerIndex() until buf.writerIndex())
                val decReadDigest = if (mod != null && privateKey != null) {
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
