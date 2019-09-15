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
package io.guthix.cache.js5.container.filesystem

import io.guthix.buffer.writeByteSUB
import java.io.IOException
import java.nio.channels.FileChannel
import io.guthix.cache.js5.container.Js5Container
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufHolder
import io.netty.buffer.DefaultByteBufHolder
import io.netty.buffer.Unpooled

/**
 * The data channel containing all cache data.
 *
 * @property fileChannel The [FileChannel] to read the data from.
 */
internal class Dat2Channel(private val fileChannel: FileChannel) : AutoCloseable {
    /**
     * The size of the file.
     */
    val size get() = fileChannel.size()

    /**
     * Reads the container data.
     *
     * @param indexFileId The index file id the [index] came from.
     * @param containerId The container file to read.
     * @param index The [Index] to read.
     */
    fun read(indexFileId: Int, containerId: Int, index: Index): ByteBuf {
        val data = Unpooled.buffer(index.dataSize)
        var segmentPart = 0
        var dataToRead = index.dataSize
        var readPointer = index.segmentNumber.toLong() * Segment.SIZE.toLong()
        do {
            val dataSegment = readSegment(readPointer)
            if (dataToRead > dataSegment.data.writerIndex()) {
                dataSegment.validate(indexFileId, containerId, segmentPart)
                data.writeBytes(dataSegment.data, 0, dataSegment.data.writerIndex())
                readPointer = dataSegment.nextSegmentNumber.toLong() * Segment.SIZE.toLong()
                dataToRead -= dataSegment.data.writerIndex()
                segmentPart++

            } else {
                data.writeBytes(dataSegment.data, 0, dataToRead)
                dataToRead = 0
            }
        } while (dataToRead > 0)
        return data
    }

    /**
     * Write the container data.
     *
     * @param indexFileId The index id to write.
     * @param containerId The container id to write.
     * @param index The [Index] to write.
     * @param totalData The data to write.
     */
    fun write(indexFileId: Int, containerId: Int, index: Index, totalData: ByteBuf) {
        val isExtended = Segment.isExtended(containerId)
        val segmentData = if(isExtended) {
            Unpooled.buffer(Segment.EXTENDED_DATA_SIZE)
        } else {
            Unpooled.buffer(Segment.DATA_SIZE)
        }
        var segmentPart = 0
        var dataToWrite = index.dataSize
        var ptr = index.segmentNumber.toLong() * Segment.SIZE.toLong()
        do {
            val overwrite = containsSegment(ptr)
            val segmentDataSize = if(dataToWrite < segmentData.capacity()) dataToWrite else segmentData.capacity()
            totalData.readBytes(segmentData, 0, segmentDataSize)
            segmentData.writerIndex(segmentDataSize)
            val segment = if(overwrite) {
                val rSeg = readSegment(ptr).validate(indexFileId, containerId, segmentPart)
                Segment(
                    rSeg.containerId,
                    rSeg.position,
                    rSeg.nextSegmentNumber,
                    rSeg.indexFileId,
                    segmentData
                )
            } else {
                val nextSegmentPos = (fileChannel.size() / Segment.SIZE) + 1
                Segment(
                    containerId,
                    segmentPart,
                    nextSegmentPos.toInt(),
                    indexFileId,
                    segmentData
                )
            }
            writeSegment(ptr, segment)
            dataToWrite -= segmentDataSize
            segmentPart++
            ptr = segment.nextSegmentNumber * Segment.SIZE.toLong()
        } while (dataToWrite > 0)
    }

    /**
     * Checks if data exists at [ptr].
     *
     * @param ptr The position in the [fileChannel] the check.
     */
    private fun containsSegment(ptr: Long) = ptr < fileChannel.size()

    /**
     * Reads a [Segment] from the [fileChannel].
     *
     * @param ptr The position to start reading.
     */
    private fun readSegment(ptr: Long): Segment {
        val buf = Unpooled.buffer(Segment.SIZE)
        buf.writeBytes(fileChannel, ptr, buf.writableBytes())
        return Segment.decode(buf)
    }

    /**
     * Writes a [Segment] to the [fileChannel].
     *
     * @param ptr The position to start writing.
     * @param segment The segment to write.
     */
    private fun writeSegment(ptr: Long, segment: Segment) {
        val buf = segment.encode()
        buf.readBytes(fileChannel, ptr, buf.readableBytes())
    }

    /**
     * Validates if the segment data matches the expected data.
     *
     * @param indexFileId The expected index file id.
     * @param containerId The expected container id.
     * @param position The expected position compared to all other [Segment]s of the same [Js5Container] data.
     */
    private fun Segment.validate(indexFileId: Int, containerId: Int, position: Int): Segment {
        if (this.indexFileId != indexFileId) throw IOException(
            "Index id mismatch expected ${this.indexFileId} was $indexFileId."
        )
        if (this.containerId != containerId) throw IOException(
            "Js5Container id mismatch expected ${this.containerId} was $containerId."
        )
        if (this.position != position) throw IOException(
            "Segment position mismatch expected ${this.position} was $position."
        )
        return this
    }

    override fun close() =  fileChannel.close()
}

/**
 * A part of container data stored on disk.
 *
 * Container data is stored in multiple Segments on disk. Segments have a fixed size and have a header containing
 * meta-data about the segment for validation. There are 2 types of segments: normal and extended. Compared to the
 * normal segments the extended segments have a longer header because their ids are higher than what can be stored in an
 * unsigned short.
 *
 * @property containerId The container id the segment belongs to
 * @property position The position of the segment relative to the other segments of the same container
 * @property nextSegmentNumber The next segment number after the current segment
 * @property indexFileId The index file id the segment belongs to
 * @property data The data of the segment
 */
internal data class Segment(
    val containerId: Int,
    val position: Int,
    val nextSegmentNumber: Int,
    val indexFileId: Int,
    val data: ByteBuf
) : DefaultByteBufHolder(data) {
    /**
     * Whether this segment is extended.
     */
    val isExtended get() = isExtended(containerId)

    /**
     * Encodes the segment.
     *
     * @param buf The buf to encode the segment to.
     */
    fun encode(buf: ByteBuf = Unpooled.buffer(SIZE)): ByteBuf {
        if (isExtended) {
            buf.writeInt(containerId)
        } else {
            buf.writeShort(containerId)
        }
        buf.writeShort(position)
        buf.writeMedium(nextSegmentNumber)
        buf.writeByte(indexFileId)
        buf.writeBytes(data.array())
        return buf
    }

    override fun copy(): Segment {
        return Segment(containerId, position, nextSegmentNumber, indexFileId, data.copy())
    }

    companion object {
        /**
         * Size of the header in bytes for a normal segment.
         */
        const val HEADER_SIZE = 8

        /**
         * Size of the container data in bytes for a normal segment.
         */
        const val DATA_SIZE = 512

        /**
         * Size of the container data in bytes for an extended segment.
         */
        const val EXTENDED_DATA_SIZE = 510

        /**
         * Size of the header in bytes for an extended segment.
         */
        const val EXTENDED_HEADER_SIZE = 10

        /**
         * Total size of a segment.
         */
        const val SIZE = HEADER_SIZE + DATA_SIZE

        /**
         * Whether a segment should be an extended segment.
         *
         * @param containerId The container id to check.
         */
        fun isExtended(containerId: Int) = containerId > UShort.MAX_VALUE.toInt()

        /**
         * Decodes a segment.
         *
         * @param containerId The container id belonging to the segment to decode.
         * @param data The buffer to decode.
         */
        fun decode(containerId: Int, data: ByteBuf): Segment = if(isExtended(containerId)) {
            decodeExtended(data)
        } else {
            decode(data)
        }

        /**
         * Decodes a normal segment.
         *
         * @param buf The buffer to decode.
         */
        fun decode(buf: ByteBuf): Segment {
            val containerId = buf.readUnsignedShort()
            val position = buf.readUnsignedShort()
            val nextSegmentNumber = buf.readUnsignedMedium()
            val indexFileId = buf.readUnsignedByte().toInt()
            val data = buf.slice(HEADER_SIZE, DATA_SIZE)
            return Segment(
                containerId,
                position,
                nextSegmentNumber,
                indexFileId,
                data
            )
        }

        /**
         * Decodes an extended segment.
         *
         * @param buf The buf to decode.
         */
        fun decodeExtended(buf: ByteBuf): Segment {
            val containerId = buf.readInt()
            val position = buf.readUnsignedShort()
            val nextSegmentNumber = buf.readUnsignedMedium()
            val indexFileId = buf.readUnsignedByte().toInt()
            val data = buf.slice(EXTENDED_HEADER_SIZE, EXTENDED_DATA_SIZE)
            return Segment(
                containerId,
                position,
                nextSegmentNumber,
                indexFileId,
                data
            )
        }
    }
}
