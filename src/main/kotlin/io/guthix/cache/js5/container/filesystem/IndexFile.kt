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

import io.guthix.cache.js5.io.putMedium
import io.guthix.cache.js5.io.uMedium
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class IDXChannel(private val fileChannel: FileChannel) : AutoCloseable {
    @ExperimentalUnsignedTypes
    internal fun read(containerId: Int): Index {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        if (ptr < 0 || ptr >= fileChannel.size()) {
            throw FileNotFoundException("Could not find container $containerId.")
        }
        val buffer = ByteBuffer.allocate(Index.SIZE)
        fileChannel.read(buffer, ptr)
        return Index.decode(buffer.flip())
    }

    internal fun write(containerId: Int, index: Index) {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        fileChannel.write(index.encode(), ptr)
    }

    internal fun containsIndex(containerId: Int): Boolean {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        return ptr < fileChannel.size()
    }

    override fun close() =  fileChannel.close()
}

internal data class Index(val dataSize: Int, val segmentNumber: Int) {
    internal fun encode(): ByteBuffer {
        val buffer = ByteBuffer.allocate(SIZE)
        buffer.putMedium(dataSize)
        buffer.putMedium(segmentNumber)
        return buffer.flip()
    }

    companion object {
        const val SIZE = 6

        @ExperimentalUnsignedTypes
        internal fun decode(buffer: ByteBuffer): Index {
            require(buffer.remaining() >= SIZE)
            val dataSize = buffer.uMedium
            val segmentPos = buffer.uMedium
            return Index(dataSize, segmentPos)
        }
    }
}
