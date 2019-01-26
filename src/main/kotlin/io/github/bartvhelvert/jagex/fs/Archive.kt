/*
GNU LGPL V3
Copyright (C) 2019 Bart van Helvert
B.A.J.v.Helvert@gmail.com

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.github.bartvhelvert.jagex.fs

import io.github.bartvhelvert.jagex.fs.io.getUByte
import io.github.bartvhelvert.jagex.fs.io.splitOf

import java.nio.ByteBuffer

data class Archive(
    val id: Int,
    val nameHash: Int?,
    val crc: Int,
    val unknownHash: Int?,
    val whirlpoolHash: ByteArray?,
    val sizes: ArchiveAttributes.Size?,
    val version: Int,
    val files: Map<Int, File>
) {
    internal fun encode(groupCount: Int = 1, containerVersion: Int = -1): Container {
        val fileBuffers = files.values.map { it.data }.toTypedArray()
        val buffer = ByteBuffer.allocate(
            fileBuffers.sumBy { it.limit() } + groupCount * fileBuffers.size * Int.SIZE_BYTES + 1
        )
        val groups = divideIntoGroups(fileBuffers, groupCount)
        for(group in groups) {
            for(fileGroup in group) {
                buffer.put(fileGroup)
            }
        }
        for(group in groups) {
            var delta = group[0].limit()
            buffer.putInt(delta)
            for(i in 1 until group.size) {
                buffer.putInt(delta - group[i].limit())
                delta = group[i].limit()
            }
        }
        buffer.put(groupCount.toByte())
        return Container(containerVersion, buffer)
    }

    private fun divideIntoGroups(
        fileBuffers: Array<ByteBuffer>,
        groupCount: Int
    ): Array<Array<ByteBuffer>> = Array(groupCount) { group ->
        Array(fileBuffers.size) { file ->
            fileBuffers[file].splitOf(group + 1, groupCount)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Archive) return false

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

    data class File(val id: Int, val data: ByteBuffer, val nameHash: Int?)

    companion object {
        @ExperimentalUnsignedTypes
        internal fun decode(container: Container, attributes: ArchiveAttributes): Archive  {
            val fileBuffers= if(attributes.fileAttributes.size == 1) {
                arrayOf(container.data)
            } else {
                decodeMultiFileContainer(container, attributes.fileAttributes.size)
            }
            val files = mutableMapOf<Int, File>()
            var index = 0
            attributes.fileAttributes.forEach { fileId, attribute ->
                files[fileId] = File(fileId, fileBuffers[index], attribute.nameHash)
                index++
            }
            return Archive(attributes.id, attributes.nameHash, attributes.crc, attributes.unknownHash,
                attributes.whirlpoolHash, attributes.sizes, attributes.version, files
            )
        }


        @ExperimentalUnsignedTypes
        internal fun decodeMultiFileContainer(container: Container, fileCount: Int): Array<ByteBuffer> {
            val buffer = container.data
            val fileSizes = IntArray(fileCount)
            val groupCount = buffer.getUByte(buffer.limit() - 1).toInt()
            val groupFileSizes = Array(groupCount) { IntArray(fileCount) }
            buffer.position(buffer.limit() - 1 - groupCount * fileCount * 4)
            for (groupId in 0 until groupCount) {
                var groupFileSize = 0
                for (fileId in 0 until fileCount) {
                    val delta = buffer.int // difference in chunk size compared to the previous chunk
                    groupFileSize += delta
                    groupFileSizes[groupId][fileId] = groupFileSize
                    fileSizes[fileId] += groupFileSize
                }
            }
            val fileData = Array<ByteBuffer>(fileCount) {
                ByteBuffer.allocate(fileSizes[it])
            }
            buffer.position(0)
            for (groupId in 0 until groupCount) {
                for (fileId in 0 until fileCount) {
                    val groupFileSize = groupFileSizes[groupId][fileId]
                    fileData[fileId].put(
                        buffer.array().sliceArray(buffer.position() until buffer.position() + groupFileSize)
                    )
                    buffer.position(buffer.position() + groupFileSize)
                }
            }
            fileData.forEach { it.flip() }
            return fileData
        }
    }
}