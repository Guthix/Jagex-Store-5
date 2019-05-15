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
package io.guthix.cache.js5.store.filesystem

import io.guthix.cache.js5.io.*
import java.io.IOException
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class Dat2Channel(private val fileChannel: FileChannel) : AutoCloseable {
    val size get() = fileChannel.size()

    @ExperimentalUnsignedTypes
    internal fun read(indexFileId: Int, index: Index, containerId: Int): ByteBuffer {
        val data = ByteBuffer.allocate(index.dataSize)
        var segmentPart = 0
        var dataToRead = index.dataSize
        var readPointer = index.segmentNumber.toLong() * Segment.SIZE.toLong()
        do {
            val dataSegment = readSegment(readPointer)
            if (dataToRead > dataSegment.data.size) {
                dataSegment.validate(indexFileId, containerId, segmentPart)
                data.put(dataSegment.data, 0, dataSegment.data.size)
                readPointer = dataSegment.nextSegmentNumber.toLong() * Segment.SIZE.toLong()
                dataToRead -= dataSegment.data.size
                segmentPart++

            } else {
                data.put(dataSegment.data, 0, dataToRead)
                dataToRead = 0
            }
        } while (dataToRead > 0)
        return (data as Buffer).flip() as ByteBuffer
    }

    @ExperimentalUnsignedTypes
    internal fun write(indexFileId: Int, containerId: Int, index: Index, buffer: ByteBuffer) {
        val isExtended = Segment.isExtended(containerId)
        val segmentData = if(isExtended) {
            ByteArray(Segment.EXTENDED_DATA_SIZE)
        } else {
            ByteArray(Segment.DATA_SIZE)
        }
        var segmentPart = 0
        var dataToWrite = index.dataSize
        var ptr = index.segmentNumber.toLong() * Segment.SIZE.toLong()
        do {
            val overwrite = containsSegment(ptr)
            val segmentDataSize = if(dataToWrite < segmentData.size) dataToWrite else segmentData.size
            buffer.get(segmentData, 0, segmentDataSize)
            val segment = if(overwrite) {
                val rSeg = readSegment(ptr).validate(indexFileId, containerId, segmentPart)
                Segment(
                    rSeg.indexFileId,
                    rSeg.containerId,
                    rSeg.position,
                    rSeg.nextSegmentNumber,
                    segmentData
                )
            } else {
                val nextSegmentPos = (fileChannel.size() / Segment.SIZE) + 1
                Segment(
                    indexFileId.toUByte(),
                    containerId,
                    segmentPart.toUShort(),
                    nextSegmentPos.toInt(),
                    segmentData
                )
            }
            writeSegment(ptr, segment)
            dataToWrite -= segmentDataSize
            segmentPart++
            ptr = segment.nextSegmentNumber * Segment.SIZE.toLong()
        } while (dataToWrite > 0)
    }

    private fun containsSegment(ptr: Long) = ptr < fileChannel.size()

    @ExperimentalUnsignedTypes
    private fun readSegment(ptr: Long): Segment {
        val buffer = ByteBuffer.allocate(Segment.SIZE)
        fileChannel.readFully(buffer, ptr)
        return Segment.decode(buffer.flip())
    }

    @ExperimentalUnsignedTypes
    private fun writeSegment(ptr: Long, segment: Segment) {
        fileChannel.position(ptr)
        fileChannel.write(segment.encode())
    }

    @ExperimentalUnsignedTypes
    private fun Segment.validate(indexFileId: Int, containerId: Int, segmentPos: Int): Segment {
        if (this.indexFileId.toInt() != indexFileId) throw IOException(
            "Index id mismatch expected ${this.indexFileId} was $indexFileId."
        )
        if (this.containerId != containerId) throw IOException(
            "Js5Container id mismatch expected ${this.containerId} was $containerId."
        )
        if (this.position.toInt() != segmentPos) throw IOException(
            "Segment position mismatch expected ${this.position} was $segmentPos."
        )
        return this
    }

    override fun close() =  fileChannel.close()
}

data class Segment @ExperimentalUnsignedTypes constructor(
    val indexFileId: UByte,
    val containerId: Int,
    val position: UShort,
    val nextSegmentNumber: Int,
    val data: ByteArray
) {
    @ExperimentalUnsignedTypes
    val isExtended get() = isExtended(containerId)

    @ExperimentalUnsignedTypes
    internal fun encode(buffer: ByteBuffer = ByteBuffer.allocate(SIZE)): ByteBuffer {
        if (isExtended) {
            buffer.putInt(containerId)
        } else {
            buffer.putShort(containerId.toShort())
        }
        buffer.putShort(position.toShort())
        buffer.putMedium(nextSegmentNumber)
        buffer.put(indexFileId.toByte())
        buffer.put(data)
        return buffer.flip()
    }

    @ExperimentalUnsignedTypes
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Segment) return false

        if (indexFileId != other.indexFileId) return false
        if (containerId != other.containerId) return false
        if (position != other.position) return false
        if (nextSegmentNumber != other.nextSegmentNumber) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    @ExperimentalUnsignedTypes
    override fun hashCode(): Int {
        var result = indexFileId.hashCode()
        result = 31 * result + containerId
        result = 31 * result + position.hashCode()
        result = 31 * result + nextSegmentNumber
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_SIZE = 8

        const val DATA_SIZE = 512

        const val EXTENDED_DATA_SIZE = 510

        const val EXTENDED_HEADER_SIZE = 10

        const val SIZE = HEADER_SIZE + DATA_SIZE

        @ExperimentalUnsignedTypes
        internal fun isExtended(containerId: Int) = containerId > UShort.MAX_VALUE.toInt()

        @ExperimentalUnsignedTypes
        internal fun decode(containerId: Int, buffer: ByteBuffer): Segment = if(isExtended(
                containerId
            )
        ) {
            decodeExtended(buffer)
        } else {
            decode(buffer)
        }

        @ExperimentalUnsignedTypes
        internal fun decode(buffer: ByteBuffer): Segment {
            val containerId = buffer.uShort.toInt()
            val position = buffer.uShort
            val nextSegmentNumber = buffer.uMedium
            val indexFileId = buffer.uByte
            val data = ByteArray(DATA_SIZE)
            buffer.get(data)
            return Segment(
                indexFileId,
                containerId,
                position,
                nextSegmentNumber,
                data
            )
        }

        @ExperimentalUnsignedTypes
        internal fun decodeExtended(buffer: ByteBuffer): Segment {
            val containerId = buffer.int
            val position = buffer.uShort
            val nextSegmentNumber = buffer.uMedium
            val indexFileId = buffer.uByte
            val data = ByteArray(EXTENDED_DATA_SIZE)
            buffer.get(data)
            return Segment(
                indexFileId,
                containerId,
                position,
                nextSegmentNumber,
                data
            )
        }
    }
}
