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

import io.guthix.js5.container.Js5Compression
import io.guthix.js5.container.Js5Container
import io.guthix.js5.container.Uncompressed
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import java.util.*
import java.util.zip.CRC32
import kotlin.math.ceil

/**
 * A set of files in the cache. A [Js5Group] is the smallest amount of data that can be read from the cache and
 * represents a set of [Js5File]s. Multiple [Js5Group]s can belong to a [Js5Archive]. To read a group from the cache
 * a [Js5Archive] must be created first. The [Js5Group] class is used as an API data class to represent the
 * [Js5GroupData] and [Js5GroupSettings] in a simple way.
 *
 * @param id The unique identifier of the group.
 * @param version The version of the group.
 * @param chunkCount The chunk count used to encode the group (only used when the group contains multiple files).
 * @param nameHash (Optional) The [String.hashCode] of the name of the group.
 * @param compression The compression used to compress the [Js5Group].
 * @param compressedCrc The [CRC32] of the compressed group data.
 * @param uncompressedCrc (Optional) The [CRC32] of the uncompressed group data.
 * @param whirlpoolHash The whirlpool hash of the encoded group data.
 * @param sizes The [Js5Container.Size] of this group as stored in the [Js5GroupSettings].
 * @param files The [Js5File]s belonging to this [Js5Group] indexed by their [Js5File.id].
 */
public data class Js5Group(
    var id: Int,
    var version: Int,
    var chunkCount: Int,
    var nameHash: Int? = null,
    var compression: Js5Compression = Uncompressed,
    internal var compressedCrc: Int = 0,
    internal var uncompressedCrc: Int? = null,
    internal var whirlpoolHash: ByteArray? = null,
    internal var sizes: Js5Container.Size? = null,
    private val files: SortedMap<Int, Js5File> = sortedMapOf(),
): SortedMap<Int, Js5File> by files {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5Group
        if (id != other.id) return false
        if (version != other.version) return false
        if (compressedCrc != other.compressedCrc) return false
        if (chunkCount != other.chunkCount) return false
        if (nameHash != other.nameHash) return false
        if (uncompressedCrc != other.uncompressedCrc) return false
        if (whirlpoolHash != null) {
            if (other.whirlpoolHash == null) return false
            if (!whirlpoolHash!!.contentEquals(other.whirlpoolHash!!)) return false
        } else if (other.whirlpoolHash != null) return false
        if (sizes != other.sizes) return false
        if (files != other.files) return false
        if (compression != other.compression) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + version
        result = 31 * result + compressedCrc
        result = 31 * result + chunkCount
        result = 31 * result + (nameHash ?: 0)
        result = 31 * result + (uncompressedCrc ?: 0)
        result = 31 * result + (whirlpoolHash?.contentHashCode() ?: 0)
        result = 31 * result + (sizes?.hashCode() ?: 0)
        result = 31 * result + files.hashCode()
        result = 31 * result + compression.hashCode()
        return result
    }

    internal companion object {
        internal fun create(data: Js5GroupData, settings: Js5GroupSettings): Js5Group {
            var i = 0
            val files = sortedMapOf<Int, Js5File>()
            settings.fileSettings.forEach { (fileId, fileSettings) ->
                files[fileId] = Js5File(fileId, fileSettings.nameHash, data.fileData[i])
                i++
            }
            return Js5Group(settings.id, settings.version, data.chunkCount, settings.nameHash, data.compression,
                settings.compressedCrc, settings.uncompressedCrc, settings.whirlpoolHash, settings.sizes, files
            )
        }
    }
}

/**
 * The data of a [Js5Group] to store in the cache.
 *
 * @param fileData The domain data for the [Js5File]s.
 * @param chunkCount The chunk count used to encode the group (only used when the group contains multiple files).
 * @param compression The compression used to compress the [Js5GroupData].
 */
internal data class Js5GroupData(
    val fileData: List<ByteBuf>,
    var chunkCount: Int = 1,
    var compression: Js5Compression = Uncompressed
) {
    /** Encodes the [Js5GroupData] into a [Js5Container]. */
    internal fun encode(version: Int? = null) = if (fileData.size == 1) {
        Js5Container(fileData.first(), compression, version)
    } else {
        Js5Container(encodeMultipleFiles(fileData, chunkCount), compression, version)
    }

    /** Encodes the [Js5GroupData] when the group contains multiple files. */
    @Suppress("ConvertLambdaToReference")
    private fun encodeMultipleFiles(data: List<ByteBuf>, chunkCount: Int): ByteBuf {
        val chunks = splitIntoChunks(data, chunkCount)
        val buf = Unpooled.compositeBuffer(
            chunks.size * chunks.sumOf { it.size } + 1
        )
        chunks.flatten().forEach { buf.addComponent(true, it) }
        val suffixBuf = Unpooled.buffer(chunkCount * data.size * Int.SIZE_BYTES + Byte.SIZE_BYTES)
        for (group in chunks) {
            var lastWrittenSize = group[0].writerIndex()
            suffixBuf.writeInt(lastWrittenSize)
            for (i in 1 until group.size) {
                suffixBuf.writeInt(group[i].writerIndex() - lastWrittenSize) // write delta
                lastWrittenSize = group[i].writerIndex()
            }
        }
        suffixBuf.writeByte(chunkCount)
        buf.addComponent(true, suffixBuf)
        return buf
    }

    /** Divides the file data into multiple chunks and split it evenly. */
    private fun splitIntoChunks(fileData: List<ByteBuf>, chunkCount: Int): List<List<ByteBuf>> {
        val chunkSize = fileData.map {
            ceil(it.writerIndex().toDouble() / chunkCount).toInt()
        }.toTypedArray()
        return List(chunkCount) { group ->
            List(fileData.size) { file ->
                fileData[file].splitOf(group, chunkSize[file])
            }
        }
    }

    /** Takes a split of a [ByteBuf] at index [index] with splits of size [length]. */
    private fun ByteBuf.splitOf(index: Int, length: Int): ByteBuf {
        val start = index * length
        return slice(index * length, if (start + length > writerIndex()) writerIndex() - start else length)
    }


    internal companion object {
        internal fun from(group: Js5Group) = Js5GroupData(
            group.values.map(Js5File::data),
            group.chunkCount,
            group.compression
        )

        /**
         * Decodes a [Js5Container] into a [Js5GroupData].
         *
         * @param container The container to decode from.
         * @param fileCount The amount of files to decode.
         */
        internal fun decode(container: Js5Container, fileCount: Int) = if (fileCount == 1) {
            Js5GroupData(listOf(container.data), 1, container.compression)
        } else {
            decodeMultipleFiles(container, fileCount)
        }

        /**
         * Decodes a [Js5Container] when the container contains multiple [Js5File]s.
         *
         * @param container The container to decode from.
         * @param fileCount The amount of files to decode.
         */
        private fun decodeMultipleFiles(container: Js5Container, fileCount: Int): Js5GroupData {
            val fileSizes = IntArray(fileCount)
            val chunkCount = container.data.getUnsignedByte(container.data.readableBytes() - 1).toInt()
            val chunkFileSizes = Array(chunkCount) { IntArray(fileCount) }
            container.data.readerIndex(container.data.readableBytes() - 1 - chunkCount * fileCount * 4)
            for (chunkId in 0 until chunkCount) {
                var groupFileSize = 0
                for (fileId in 0 until fileCount) {
                    val delta = container.data.readInt() // difference in chunk size compared to the previous chunk
                    groupFileSize += delta
                    chunkFileSizes[chunkId][fileId] = groupFileSize
                    fileSizes[fileId] += groupFileSize
                }
            }
            val fileData = Array<CompositeByteBuf>(fileCount) {
                Unpooled.compositeBuffer(chunkCount)
            }
            container.data.readerIndex(0)
            for (chunkId in 0 until chunkCount) {
                for (fileId in 0 until fileCount) {
                    val groupFileSize = chunkFileSizes[chunkId][fileId]
                    fileData[fileId].addComponent(
                        true, container.data.slice(container.data.readerIndex(), groupFileSize)
                    )
                    container.data.readerIndex(container.data.readerIndex() + groupFileSize)
                }
            }
            return Js5GroupData(
                fileData.map { it.asReadOnly() },
                chunkCount,
                container.compression
            )
        }
    }
}

/**
 * The settings for a [Js5GroupData]. The [Js5GroupSettings] contain meta-data about [Js5GroupData]s. The
 * [Js5GroupSettings] are encoded in the [Js5ArchiveSettings].
 *
 * @property id The unique identifier in the archive of this group.
 * @property nameHash (Optional) The unique string identifier in the archive stored as a [java.lang.String.hashCode].
 * @property compressedCrc The [java.util.zip.CRC32] value of the encoded [Js5GroupData] data.
 * @property uncompressedCrc (Optional) Its purpose and type is unknown as of yet.
 * @property whirlpoolHash (Optional) A whirlpool hash with its purpose unknown.
 * @property sizes (Optional) The [Js5Container.Size] of this [Js5GroupData].
 * @property version The version of this group.
 * @property fileSettings The [Js5FileSettings] for each file in a map indexed by their id.
 */
public data class Js5GroupSettings(
    var id: Int,
    var version: Int,
    var compressedCrc: Int,
    var nameHash: Int? = null,
    var uncompressedCrc: Int? = null,
    var whirlpoolHash: ByteArray? = null,
    var sizes: Js5Container.Size? = null,
    val fileSettings: SortedMap<Int, Js5FileSettings>,
): SortedMap<Int, Js5FileSettings> by fileSettings {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Js5GroupSettings
        if (id != other.id) return false
        if (version != other.version) return false
        if (compressedCrc != other.compressedCrc) return false
        if (fileSettings != other.fileSettings) return false
        if (nameHash != other.nameHash) return false
        if (uncompressedCrc != other.uncompressedCrc) return false
        if (whirlpoolHash != null) {
            if (other.whirlpoolHash == null) return false
            if (!whirlpoolHash!!.contentEquals(other.whirlpoolHash!!)) return false
        } else if (other.whirlpoolHash != null) return false
        if (sizes != other.sizes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + version
        result = 31 * result + compressedCrc
        result = 31 * result + fileSettings.hashCode()
        result = 31 * result + (nameHash ?: 0)
        result = 31 * result + (uncompressedCrc ?: 0)
        result = 31 * result + (whirlpoolHash?.contentHashCode() ?: 0)
        result = 31 * result + (sizes?.hashCode() ?: 0)
        return result
    }

    public companion object {
        internal fun from(group: Js5Group) = Js5GroupSettings(
            group.id,
            group.version,
            group.compressedCrc,
            group.nameHash,
            group.uncompressedCrc,
            group.whirlpoolHash,
            group.sizes,
            group.mapValues { (fileId, file) -> Js5FileSettings(fileId, file.nameHash) }.toSortedMap(),
        )
    }
}