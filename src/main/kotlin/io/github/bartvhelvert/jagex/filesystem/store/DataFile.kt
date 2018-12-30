package io.github.bartvhelvert.jagex.filesystem.store

import io.github.bartvhelvert.jagex.filesystem.io.putMedium
import io.github.bartvhelvert.jagex.filesystem.io.uByte
import io.github.bartvhelvert.jagex.filesystem.io.uMedium
import io.github.bartvhelvert.jagex.filesystem.io.uShort
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class DataChannel(private val fileChannel: FileChannel) {
    @ExperimentalUnsignedTypes
    internal fun read(indexFileId: Int, index: Index, containerId: Int): ByteBuffer {
        val data = ByteBuffer.allocate(index.dataSize)
        var segmentPart = 0
        var dataToRead = index.dataSize
        var readPointer = index.segmentPos.toLong() * Segment.SIZE.toLong()
        do {
            val dataSegment = readSegment(readPointer)
            if (dataToRead > dataSegment.data.size) {
                dataSegment.validate(indexFileId, containerId, segmentPart)
                data.put(dataSegment.data, 0, dataSegment.data.size)
                readPointer = dataSegment.nextSegmentPos.toLong() * Segment.SIZE.toLong()
                dataToRead -= dataSegment.data.size
                segmentPart++

            } else {
                data.put(dataSegment.data, 0, dataToRead)
                dataToRead = 0
            }
        } while (dataToRead > 0)
        return data.flip() as ByteBuffer
    }

    @ExperimentalUnsignedTypes
    internal fun write(indexFileId: Int, containerId: Int, index: Index, buffer: ByteBuffer) {
        val isExtended = Segment.isExtended(containerId)
        val segmentData = if(isExtended) ByteArray(Segment.EXTENDED_DATA_SIZE) else ByteArray(Segment.DATA_SIZE)
        var segmentPart = 0
        var dataToWrite = index.dataSize
        var ptr = index.segmentPos.toLong() * Segment.SIZE.toLong()
        do {
            val overwrite = containsSegment(ptr)
            val segmentDataSize = if(dataToWrite < segmentData.size) dataToWrite else segmentData.size
            buffer.get(segmentData, 0, segmentDataSize)
            val segment = if(overwrite) {
                val rSeg = readSegment(ptr).validate(indexFileId, containerId, segmentPart)
                Segment(rSeg.indexFileId, rSeg.containerId, rSeg.segmentPart, rSeg.nextSegmentPos, segmentData)
            } else {
                val nextSegmentPos = (fileChannel.size() / Segment.SIZE) + 1
                Segment(indexFileId.toUByte(), containerId, segmentPart.toUShort(), nextSegmentPos.toInt(), segmentData)
            }
            writeSegment(ptr, segment)
            dataToWrite -= segmentDataSize
            segmentPart++
            ptr = segment.nextSegmentPos * Segment.SIZE.toLong()
        } while (dataToWrite > 0)
    }

    private fun containsSegment(ptr: Long) = ptr < 0 || ptr >= fileChannel.size()

    @ExperimentalUnsignedTypes
    private fun readSegment(ptr: Long): Segment {
        val buffer = ByteBuffer.allocate(Segment.SIZE)
        fileChannel.position(ptr)
        fileChannel.read(buffer)
        return Segment.decode(buffer.flip() as ByteBuffer)
    }

    @ExperimentalUnsignedTypes
    private fun writeSegment(ptr: Long, segment: Segment) {
        fileChannel.position(ptr)
        fileChannel.write(segment.encode())
    }

    @ExperimentalUnsignedTypes
    private fun Segment.validate(indexFileId: Int, containerId: Int, segmentPos: Int): Segment {
        if (this.indexFileId.toInt() != indexFileId) throw IOException("Index id mismatch")
        if (this.containerId != containerId) throw IOException("Archive id mismatch")
        if (this.segmentPart.toInt() != segmentPos) throw IOException("Segment position mismatch")
        return this
    }
}

internal data class Segment @ExperimentalUnsignedTypes constructor(
    val indexFileId: UByte,
    val containerId: Int,
    val segmentPart: UShort,
    val nextSegmentPos: Int,
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
        buffer.putShort(segmentPart.toShort())
        buffer.putMedium(nextSegmentPos)
        buffer.put(indexFileId.toByte())
        buffer.put(data)
        return buffer.flip() as ByteBuffer
    }

    @ExperimentalUnsignedTypes
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Segment) return false

        if (indexFileId != other.indexFileId) return false
        if (containerId != other.containerId) return false
        if (segmentPart != other.segmentPart) return false
        if (nextSegmentPos != other.nextSegmentPos) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    @ExperimentalUnsignedTypes
    override fun hashCode(): Int {
        var result = indexFileId.hashCode()
        result = 31 * result + containerId
        result = 31 * result + segmentPart.hashCode()
        result = 31 * result + nextSegmentPos
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
        fun isExtended(containerId: Int) = containerId > UShort.MAX_VALUE.toInt()

        @ExperimentalUnsignedTypes
        fun decode(containerId: Int, buffer: ByteBuffer): Segment = if(isExtended(containerId)) {
            decodeExtended(buffer)
        } else {
            decode(buffer)
        }

        @ExperimentalUnsignedTypes
        fun decode(buffer: ByteBuffer): Segment {
            val containerId = buffer.uShort.toInt()
            val segmentPart = buffer.uShort
            val nextSegmentPos = buffer.uMedium
            val indexId = buffer.uByte
            val data = ByteArray(DATA_SIZE)
            buffer.get(data)
            return Segment(indexId, containerId, segmentPart, nextSegmentPos, data)
        }

        @ExperimentalUnsignedTypes
        fun decodeExtended(buffer: ByteBuffer): Segment {
            val containerId = buffer.int
            val segmentPart = buffer.uShort
            val nextSegmentPos = buffer.uMedium
            val indexId = buffer.uByte
            val data = ByteArray(EXTENDED_DATA_SIZE)
            buffer.get(data)
            return Segment(indexId, containerId, segmentPart, nextSegmentPos, data)
        }
    }
}