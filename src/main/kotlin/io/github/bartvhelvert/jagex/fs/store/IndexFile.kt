package io.github.bartvhelvert.jagex.fs.store

import io.github.bartvhelvert.jagex.fs.io.putMedium
import io.github.bartvhelvert.jagex.fs.io.uMedium
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class IndexChannel(private val fileChannel: FileChannel) {
    val dataSize get() = fileChannel.size()

    @ExperimentalUnsignedTypes
    internal fun read(containerId: Int): Index {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        if (ptr < 0 || ptr >= fileChannel.size())
            throw FileNotFoundException("Could not find index for archive $containerId")
        val buffer = ByteBuffer.allocate(Index.SIZE)
        fileChannel.read(buffer, ptr)
        return Index.decode(buffer.flip() as ByteBuffer)
    }

    internal fun write(containerId: Int, index: Index) {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        fileChannel.write(index.encode(), ptr)
    }

    internal fun containsIndex(containerId: Int): Boolean {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        return ptr < 0 || ptr >= fileChannel.size()
    }
}

internal data class Index(val dataSize: Int, val segmentPos: Int) {
    internal fun encode(buffer: ByteBuffer = ByteBuffer.allocate(SIZE)): ByteBuffer {
        buffer.putMedium(dataSize)
        buffer.putMedium(segmentPos)
        return buffer.flip() as ByteBuffer
    }

    companion object {
        const val SIZE = 6

        @ExperimentalUnsignedTypes
        internal fun decode(buffer: ByteBuffer): Index {
            require(buffer.remaining() >= SIZE)
            val size = buffer.uMedium
            val sector = buffer.uMedium
            return Index(size, sector)
        }
    }
}