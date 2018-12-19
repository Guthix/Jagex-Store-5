package io.github.bartvhelvert.jagex.filesystem.store

import io.github.bartvhelvert.jagex.filesystem.putMedium
import io.github.bartvhelvert.jagex.filesystem.uMedium
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class IndexChannel(private val fileChannel: FileChannel) {
    @ExperimentalUnsignedTypes
    fun read(archiveId: Int): Index {
        val ptr = archiveId.toLong() * Index.SIZE.toLong()
        if (ptr < 0 || ptr >= fileChannel.size()) throw FileNotFoundException()
        val buffer = ByteBuffer.allocate(Index.SIZE)
        fileChannel.read(buffer)
        return Index.decode(buffer)
    }
}

internal data class Index(val dataSize: Int, val segmentPos: Int) {
    fun encode(buffer: ByteBuffer = ByteBuffer.allocate(SIZE)): ByteBuffer {
        buffer.putMedium(dataSize)
        buffer.putMedium(segmentPos)
        return buffer.flip() as ByteBuffer
    }

    companion object {
        const val SIZE = 6

        @ExperimentalUnsignedTypes
        fun decode(buffer: ByteBuffer): Index {
            require(buffer.remaining() >= SIZE)
            val size = buffer.uMedium
            val sector = buffer.uMedium
            return Index(size, sector)
        }
    }
}