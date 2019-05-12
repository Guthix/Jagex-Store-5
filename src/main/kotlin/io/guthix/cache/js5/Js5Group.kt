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
import java.nio.Buffer

import java.nio.ByteBuffer

data class Js5Group(
    val id: Int,
    val nameHash: Int?,
    val crc: Int,
    val unknownHash: Int?,
    val whirlpoolHash: ByteArray?,
    val sizes: Js5GroupSettings.Size?,
    val version: Int,
    val files: Map<Int, File>
) {
    internal fun encode(chunkCount: Int = 1, containerVersion: Int = -1): Js5Container {
        val fileBuffers = files.values.map { it.data }.toTypedArray()
        val buffer = ByteBuffer.allocate(
            fileBuffers.sumBy { it.limit() } + chunkCount * fileBuffers.size * Int.SIZE_BYTES + 1
        )
        val chunks = splitIntoChunks(fileBuffers, chunkCount)
        for(group in chunks) {
            for(fileGroup in group) {
                buffer.put(fileGroup)
            }
        }
        for(group in chunks) {
            var lastWrittenSize = group[0].limit()
            buffer.putInt(lastWrittenSize)
            for(i in 1 until group.size) {
                buffer.putInt(group[i].limit() - lastWrittenSize) // write delta
                lastWrittenSize = group[i].limit()
            }
        }
        buffer.put(chunkCount.toByte())
        return Js5Container(containerVersion, buffer)
    }

    private fun splitIntoChunks(
        fileBuffers: Array<ByteBuffer>,
        groupCount: Int
    ): Array<Array<ByteBuffer>> = Array(groupCount) { group ->
        Array(fileBuffers.size) { file ->
            fileBuffers[file].splitOf(group + 1, groupCount)
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
        result = 31 * result + version
        result = 31 * result + files.hashCode()
        return result
    }

    data class File(val data: ByteBuffer, val nameHash: Int?)

    companion object {
        @ExperimentalUnsignedTypes
        internal fun decode(js5Container: Js5Container, groupSettings: Js5GroupSettings): Js5Group {
            val fileBuffers = if(groupSettings.fileSettings.size == 1) {
                arrayOf(js5Container.data)
            } else {
                decodeMultiFileContainer(js5Container, groupSettings.fileSettings.size)
            }
            val files = mutableMapOf<Int, File>()
            var index = 0
            groupSettings.fileSettings.forEach { fileId, attribute ->
                files[fileId] = File(fileBuffers[index], attribute.nameHash)
                index++
            }
            return Js5Group(
                groupSettings.id, groupSettings.nameHash, groupSettings.crc, groupSettings.unknownHash,
                groupSettings.whirlpoolHash, groupSettings.sizes, groupSettings.version, files
            )
        }


        @ExperimentalUnsignedTypes
        internal fun decodeMultiFileContainer(js5Container: Js5Container, fileCount: Int): Array<ByteBuffer> {
            val buffer = js5Container.data
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
            fileData.forEach { (it as Buffer).flip() as ByteBuffer }
            return fileData
        }
    }
}
