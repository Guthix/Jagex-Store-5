/*
 * Copyright 2018-2021 Guthix
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.guthix.js5.container.disk

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.FileNotFoundException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val logger = KotlinLogging.logger {}

/**
 * An [IdxFile] containing [Index]es of data in a [Dat2File]. The [IdxFile] contains a sequence of [Index]es.
 * The position of an [Index] in the [IdxFile] determines the container id of the data the index points to.
 *
 * @property id The id belonging to this [IdxFile].
 * @property fileChannel The [FileChannel] to read the [Index]es from.
 */
public class IdxFile private constructor(public val id: Int, private val fileChannel: FileChannel) : AutoCloseable {
    /**
     * The size of the [IdxFile].
     */
    public val size: Long get() = fileChannel.size()

    /**
     * Reads an [Index] from the [fileChannel].
     */
    public fun read(containerId: Int): Index {
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
    public fun write(containerId: Int, index: Index) {
        val buf = index.encode()
        buf.readBytes(fileChannel, containerId.toLong() * Index.SIZE.toLong(), buf.readableBytes())
    }

    /**
     * Removes an [Index] from the [fileChannel].
     */
    public fun remove(containerId: Int) {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        Index.EMPTY_BUF.getBytes(0, fileChannel, ptr, Index.SIZE)
    }

    /**
     * Checks whether an [Index] exists in this file.
     *
     * @param containerId The container to check.
     */
    public fun containsIndex(containerId: Int): Boolean {
        val ptr = containerId.toLong() * Index.SIZE.toLong()
        return ptr < fileChannel.size()
    }

    override fun close(): Unit = fileChannel.close()

    public companion object {
        /**
         * The prefix of the [IdxFile] file extension.
         */
        internal const val EXTENSION = "idx"

        /**
         * Opens an [IdxFile].
         */
        public fun open(id: Int, path: Path): IdxFile {
            if (Files.exists(path)) {
                logger.debug { "Found .idx$id file" }
            } else throw FileNotFoundException(
                "Could not find .idx$id file at $path."
            )
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
public data class Index(val dataSize: Int, val sectorNumber: Int) {
    /**
     * Encodes the [Index].
     */
    public fun encode(): ByteBuf {
        val buf = Unpooled.buffer(SIZE)
        buf.writeMedium(dataSize)
        buf.writeMedium(sectorNumber)
        return buf
    }

    public companion object {
        /**
         * [Byte] size of the [Index].
         */
        public const val SIZE: Int = 6

        /**
         * An empty encoded [Index].
         */
        public val EMPTY_BUF: ByteBuf = Index(dataSize = 0, sectorNumber = 0).encode()

        /**
         * Decodes an [Index].
         */
        public fun decode(data: ByteBuf): Index {
            val dataSize = data.readUnsignedMedium()
            val sectorNumber = data.readUnsignedMedium()
            return Index(dataSize, sectorNumber)
        }
    }
}
