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

import io.guthix.cache.js5.io.getUByte
import io.guthix.cache.js5.io.splitOf
import io.guthix.cache.js5.container.Js5Container

import java.nio.ByteBuffer

/**
 * A collection of [File]s that can be read from a cache.
 *
 * A [Js5Group] is a collection of [File]s where each file is accompanied an ID. The [Js5Group] group also has its own
 * ID. Multiple [Js5Group]s can form an archive. The [Js5Group] is the smallest set of files that can be read/written
 * from/to the cache and can be encoded/decoded from/to a [Js5Container]. Thus a request for a single file in the
 * [Js5Group] requires the whole group to be read. Each [Js5Group] can also contain other meta-data that is loaded from
 * the [Js5GroupSettings].
 *
 * @property id The unique identifier in the archive of this group.
 * @property nameHash (Optional) The unique string identifier in the archive stored as a [java.lang.String.hashCode].
 * @property crc The [java.util.zip.CRC32] value of the encoded [Js5Group] data.
 * @property unknownHash (Optional) Its purpose and type is unknown as of yet.
 * @property whirlpoolHash (Optional) A whirlpool hash with its purpose unknown.
 * @property sizes (Optional) The [Js5GroupSettings.Size] of this [Js5Group].
 * @property version The version of this group.
 * @property files The [File]s in a map, indexed by their [id].
 */
data class Js5Group(
    val id: Int,
    val nameHash: Int?,
    val crc: Int,
    val unknownHash: Int?,
    val whirlpoolHash: ByteArray?,
    var sizes: Js5GroupSettings.Size?,
    val version: Int,
    val files: MutableMap<Int, File>
) {
    /**
     * Encodes a [Js5Group] into a [Js5Container]. If the [Js5Group] contains a single file the data of that file will
     * be used as the [Js5Group] encoding. If the [Js5Group] contains more than 1 file the files can be split up in
     * chunks of data.
     *
     * @see [decode] for doing the reverse operation.
     *
     * @param chunkCount The amount of chunks used to split the files when there is more than 1 file.
     */
    fun encode(chunkCount: Int = 1): Js5Container {
        if(files.values.size == 1) {
            return Js5Container(version, files.values.first().data)
        }
        val fileBuffers = files.values.map { it.data }.toTypedArray()
        val buffer = ByteBuffer.allocate(
            fileBuffers.sumBy { it.size } + chunkCount * fileBuffers.size * Int.SIZE_BYTES + 1
        )
        val chunks = splitIntoChunks(fileBuffers, chunkCount)
        for(group in chunks) {
            for(fileGroup in group) {
                buffer.put(fileGroup)
            }
        }
        for(group in chunks) {
            var lastWrittenSize = group[0].size
            buffer.putInt(lastWrittenSize)
            for(i in 1 until group.size) {
                buffer.putInt(group[i].size - lastWrittenSize) // write delta
                lastWrittenSize = group[i].size
            }
        }
        buffer.put(chunkCount.toByte())
        return Js5Container(version, buffer.array())
    }

    /**
     * Splits a set of [ByteArray]s int [chunkCount] chunks.
     *
     * @param fileData The data do split
     * @param chunkCount The amount of chunks to split the data into
     */
    private fun splitIntoChunks(
        fileData: Array<ByteArray>,
        chunkCount: Int
    ): Array<Array<ByteArray>> = Array(chunkCount) { group ->
        Array(fileData.size) { file ->
            fileData[file].splitOf(group + 1, chunkCount)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Js5Group) return false
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
        if (files != other.files) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + (nameHash ?: 0)
        result = 31 * result + crc
        result = 31 * result + (unknownHash ?: 0)
        result = 31 * result + (whirlpoolHash?.contentHashCode() ?: 0)
        result = 31 * result + (sizes?.hashCode() ?: 0)
        result = 31 * result + (version)
        result = 31 * result + files.hashCode()
        return result
    }

    /**
     * The smallest data unit in a [Js5Cache]. Each file contains data and optionally has a [nameHash].
     *
     * @property nameHash (Optional) The unique string identifier in the [Js5Group] stored as a
     * [java.lang.String.hashCode].
     * @property data The data of the file.
     */
    data class File(val nameHash: Int?, val data: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as File

            if (nameHash != other.nameHash) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = nameHash ?: 0
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    companion object {
        /**
         * Decodes a [Js5Container] into a [Js5Group]. If the [Js5Group] contains a single file the whole [Js5Container]
         * data is used as [File] data. If the [Js5Group] contains more than 1 file the files could be split up in
         * multiple chunks of data
         *
         * @see [encode] for doing the reverse operation.
         *
         * @param js5Container The container to decode from.
         * @param groupSettings The [Js5GroupSettings] from the master index belonging to this group.
         */
        fun decode(js5Container: Js5Container, groupSettings: Js5GroupSettings): Js5Group {
            val fileBuffers = if(groupSettings.fileSettings.size == 1) {
                arrayOf(js5Container.data)
            } else {
                decodeMultiFileContainer(js5Container, groupSettings.fileSettings.size)
            }
            val files = mutableMapOf<Int, File>()
            var index = 0
            groupSettings.fileSettings.forEach { (fileId, attribute) ->
                files[fileId] = File(attribute.nameHash, fileBuffers[index])
                index++
            }
            return Js5Group(
                groupSettings.id, groupSettings.nameHash, groupSettings.crc, groupSettings.unknownHash,
                groupSettings.whirlpoolHash, groupSettings.sizes, groupSettings.version, files
            )
        }

        /**
         * Decodes a [Js5Container] when the container contains multiple [File]s.
         *
         * @param js5Container The container to decode from.
         * @param fileCount The amount of files to decode.
         */
        private fun decodeMultiFileContainer(js5Container: Js5Container, fileCount: Int): Array<ByteArray> {
            val buffer = ByteBuffer.wrap(js5Container.data)
            val fileSizes = IntArray(fileCount)
            val chunkCount = buffer.getUByte(buffer.limit() - 1).toInt()
            val chunkFileSizes = Array(chunkCount) { IntArray(fileCount) }
            buffer.position(buffer.limit() - 1 - chunkCount * fileCount * 4)
            for (chunkId in 0 until chunkCount) {
                var groupFileSize = 0
                for (fileId in 0 until fileCount) {
                    val delta = buffer.int // difference in chunk size compared to the previous chunk
                    groupFileSize += delta
                    chunkFileSizes[chunkId][fileId] = groupFileSize
                    fileSizes[fileId] += groupFileSize
                }
            }
            val fileData = Array<ByteBuffer>(fileCount) {
                ByteBuffer.allocate(fileSizes[it])
            }
            buffer.position(0)
            for (chunkId in 0 until chunkCount) {
                for (fileId in 0 until fileCount) {
                    val groupFileSize = chunkFileSizes[chunkId][fileId]
                    fileData[fileId].put(
                        buffer.array().sliceArray(buffer.position() until buffer.position() + groupFileSize)
                    )
                    buffer.position(buffer.position() + groupFileSize)
                }
            }
            return fileData.map { it.array() }.toTypedArray()
        }
    }
}
