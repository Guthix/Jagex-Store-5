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
package io.guthix.cache.js5.container.disk

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.FileNotFoundException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import io.guthix.cache.js5.container.Js5Container

/**
 * An index file for writing and reading [Index]es.
 *
 * An index is a pointer to a file in a [Dat2File]. They are sequentially stored in index files as fixed sized volumes
 * indexed by the [Js5Container] id.
 *
 * @property fileChannel The [FileChannel] to read the indices from.
 */
class IdxFile private constructor(val id: Int, private val fileChannel: FileChannel) : AutoCloseable {
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
     * Removes an index from the [fileChannel].
     */
    internal fun remove(containerId: Int) {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        Index.EMPTY_BUF.getBytes(0, fileChannel, ptr, 6)
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

    override fun close() = fileChannel.close()

    companion object {
        const val EXTENSION = "idx"

        fun open(id: Int, path: Path): IdxFile {
            return IdxFile(id, FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE))
        }
    }
}

/**
 * An [Index] stored in an [IdxFile].
 *
 * @property dataSize The size of the data of which the index points to.
 * @property sectorNumber The [Sector] where the data starts in the [Dat2File].
 */
internal data class Index(val dataSize: Int, val sectorNumber: Int) {
    /**
     * Encodes the index.
     */
    fun encode(): ByteBuf {
        val buf = Unpooled.buffer(SIZE)
        buf.writeMedium(dataSize)
        buf.writeMedium(sectorNumber)
        return buf
    }

    companion object {
        /**
         * Byte size of the [Index].
         */
        const val SIZE = 6

        val EMPTY_BUF = Index(dataSize = 0, sectorNumber = 0).encode()

        /**
         * Decodes an [Index].
         *
         * @param data The data to decode.
         */
        fun decode(data: ByteBuf): Index {
            val dataSize = data.readUnsignedMedium()
            val sectorNumber = data.readUnsignedMedium()
            return Index(dataSize, sectorNumber)
        }
    }
}
