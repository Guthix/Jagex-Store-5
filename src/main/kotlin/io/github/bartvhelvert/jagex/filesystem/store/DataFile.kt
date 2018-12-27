package io.github.bartvhelvert.jagex.filesystem.store

import io.github.bartvhelvert.jagex.filesystem.Container
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
        var segmentNumber = 0
        var dataToRead = index.dataSize
        var readPointer = index.segmentPos.toLong() * Segment.SIZE.toLong()
        do {
            val dataSegment = readSegment(readPointer)
            if (dataToRead > dataSegment.data.size) {
                dataSegment.validate(indexFileId, containerId, segmentNumber)
                data.put(dataSegment.data, 0, dataSegment.data.size)
                readPointer = dataSegment.nextSegmentPos.toLong() * Segment.SIZE.toLong()
                dataToRead -= dataSegment.data.size
                segmentNumber++

            } else {
                data.put(dataSegment.data, 0, dataToRead)
                dataToRead = 0
            }
        } while (dataToRead > 0)
        return data.flip() as ByteBuffer
    }

    internal fun write(index: Index, buffer: ByteBuffer, overwrite: Boolean) {
        var segmentNumber = 0
        var dataToWrite = index.dataSize
    }

    @ExperimentalUnsignedTypes
    private fun readSegment(readPointer: Long): Segment {
        val buffer = ByteBuffer.allocate(Segment.SIZE)
        fileChannel.position(readPointer)
        fileChannel.read(buffer)
        return Segment.decode(buffer.flip() as ByteBuffer)
    }

    @ExperimentalUnsignedTypes
    private fun Segment.validate(indexId: Int, containerId: Int, segmentNumber: Int) {
        if (this.indexId.toInt() != indexId) throw IOException("Index id mismatch")
        if (this.containerId != containerId) throw IOException("Archive id mismatch")
        if (this.segmentPos.toInt() != segmentNumber) throw IOException("Chunk id mismatch")
    }
}

internal data class Segment @ExperimentalUnsignedTypes constructor(
    val indexId: UByte,
    val containerId: Int,
    val segmentPos: UShort,
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
        buffer.putShort(segmentPos.toShort())
        buffer.putMedium(nextSegmentPos)
        buffer.put(indexId.toByte())
        buffer.put(data)
        return buffer.flip() as ByteBuffer
    }

    @ExperimentalUnsignedTypes
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Segment) return false

        if (indexId != other.indexId) return false
        if (containerId != other.containerId) return false
        if (segmentPos != other.segmentPos) return false
        if (nextSegmentPos != other.nextSegmentPos) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    @ExperimentalUnsignedTypes
    override fun hashCode(): Int {
        var result = indexId.hashCode()
        result = 31 * result + containerId
        result = 31 * result + segmentPos.hashCode()
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
        fun isExtended(archiveId: Int) = archiveId > UShort.MAX_VALUE.toInt()

        @ExperimentalUnsignedTypes
        fun decode(archiveId: Int, buffer: ByteBuffer): Segment = if(isExtended(archiveId)) {
            decodeExtended(buffer)
        } else {
            decode(buffer)
        }

        @ExperimentalUnsignedTypes
        fun decode(buffer: ByteBuffer): Segment {
            val archiveId = buffer.uShort.toInt()
            val segmentPos = buffer.uShort
            val nextSegmentPos = buffer.uMedium
            val indexId = buffer.uByte
            val data = ByteArray(DATA_SIZE)
            buffer.get(data)
            return Segment(indexId, archiveId, segmentPos, nextSegmentPos, data)
        }

        @ExperimentalUnsignedTypes
        fun decodeExtended(buffer: ByteBuffer): Segment {
            val archiveId = buffer.int
            val segmentPos = buffer.uShort
            val nextSegmentPos = buffer.uMedium
            val indexId = buffer.uByte
            val data = ByteArray(EXTENDED_DATA_SIZE)
            buffer.get(data)
            return Segment(indexId, archiveId, segmentPos, nextSegmentPos, data)
        }
    }
}