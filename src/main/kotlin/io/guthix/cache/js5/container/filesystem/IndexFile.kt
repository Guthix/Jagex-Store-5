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

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.FileNotFoundException
import java.nio.channels.FileChannel

/**
 * An index file channel for writing and reading indices.
 *
 * An index is a pointer to a file in a [Dat2Channel]. They are sequentially stored in index files.
 *
 * @property fileChannel The [FileChannel] to read the indices from.
 */
internal class IDXChannel(private val fileChannel: FileChannel) : AutoCloseable {
    /**
     * The size of the file.
     */
    val size get() = fileChannel.size()

    /**
     * Reads an index from the [fileChannel].
     *
     * @param containerId The container to read.
     */
    internal fun read(containerId: Int): Index {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        if (ptr < 0 || ptr >= fileChannel.size()) {
            throw FileNotFoundException("Could not find container $containerId.")
        }
        val buf = Unpooled.buffer(Index.SIZE)
        buf.writeBytes(fileChannel, ptr, buf.writableBytes())
        return Index.decode(buf)
    }

    /**
     * Writes an index to the [fileChannel].
     *
     * @param containerId The container to write.
     * @param index The index to write.
     */
    internal fun write(containerId: Int, index: Index) {
        val buf = index.encode()
        buf.readBytes(fileChannel, containerId.toLong() * Index.SIZE.toLong(), buf.readableBytes())
    }

    /**
     * Checks whether an index exists in this file.
     *
     * @param containerId The container to check.
     */
    internal fun containsIndex(containerId: Int): Boolean {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        return ptr < fileChannel.size()
    }

    override fun close() =  fileChannel.close()
}

/**
 * An index in an [IDXChannel].
 *
 * @property dataSize The size of the data of which the index points to.
 * @property segmentNumber The relative position of this index compared to other index that point to the same file.
 */
internal data class Index(val dataSize: Int, val segmentNumber: Int) {
    /**
     * Encodes the index.
     */
    fun encode(): ByteBuf {
        val buf = Unpooled.buffer(SIZE)
        buf.writeMedium(dataSize)
        buf.writeMedium(segmentNumber)
        return buf
    }

    companion object {
        /**
         * Byte size of the index.
         */
        const val SIZE = 6

        /**
         * Decodes an index.
         *
         * @param data The data to decode.
         */
        fun decode(data: ByteBuf): Index {
            val dataSize = data.readUnsignedMedium()
            val segmentPos = data.readUnsignedMedium()
            return Index(dataSize, segmentPos)
        }
    }
}
