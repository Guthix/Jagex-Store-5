/*
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5.container.disk

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.FileNotFoundException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * An [IdxFile] containing [Index]es of data in a [Dat2File]. The [IdxFile] contains a sequence of [Index]es.
 * The position of an [Index] in the [IdxFile] determines the container id of the data the index points to.
 *
 * @property id The id belonging to this [IdxFile].
 * @property fileChannel The [FileChannel] to read the [Index]es from.
 */
internal class IdxFile private constructor(val id: Int, private val fileChannel: FileChannel) : AutoCloseable {
    /**
     * The size of the [IdxFile].
     */
    internal val size get() = fileChannel.size()

    /**
     * Reads an [Index] from the [fileChannel].
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
     * Writes an [Index] to the [fileChannel].
     */
    internal fun write(containerId: Int, index: Index) {
        val buf = index.encode()
        buf.readBytes(fileChannel, containerId.toLong() * Index.SIZE.toLong(), buf.readableBytes())
    }

    /**
     * Removes an [Index] from the [fileChannel].
     */
    internal fun remove(containerId: Int) {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        Index.EMPTY_BUF.getBytes(0, fileChannel, ptr, Index.SIZE)
    }

    /**
     * Checks whether an [Index] exists in this file.
     *
     * @param containerId The container to check.
     */
    internal fun containsIndex(containerId: Int): Boolean {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        return ptr < fileChannel.size()
    }

    override fun close() = fileChannel.close()

    companion object {
        /**
         * The prefix of the [IdxFile] file extension.
         */
        internal const val EXTENSION = "idx"

        /**
         * Opens an [IdxFile].
         */
        fun open(id: Int, path: Path): IdxFile {
            return IdxFile(id, FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE))
        }
    }
}

/**
 * An [Index] stored in an [IdxFile].
 *
 * @property dataSize The size of the data in [Byte]s of which the index points to.
 * @property sectorNumber The [Sector] where the data starts in the [Dat2File].
 */
internal data class Index(val dataSize: Int, val sectorNumber: Int) {
    /**
     * Encodes the [Index].
     */
    fun encode(): ByteBuf {
        val buf = Unpooled.buffer(SIZE)
        buf.writeMedium(dataSize)
        buf.writeMedium(sectorNumber)
        return buf
    }

    companion object {
        /**
         * [Byte] size of the [Index].
         */
        const val SIZE = 6

        /**
         * An empty encoded [Index].
         */
        val EMPTY_BUF = Index(dataSize = 0, sectorNumber = 0).encode()

        /**
         * Decodes an [Index].
         */
        fun decode(data: ByteBuf): Index {
            val dataSize = data.readUnsignedMedium()
            val sectorNumber = data.readUnsignedMedium()
            return Index(dataSize, sectorNumber)
        }
    }
}
