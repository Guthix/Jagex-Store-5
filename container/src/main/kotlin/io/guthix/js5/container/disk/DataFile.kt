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

import io.guthix.js5.container.Js5Container
import io.netty.buffer.ByteBuf
import io.netty.buffer.DefaultByteBufHolder
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.ceil

private val logger = KotlinLogging.logger {}

/**
 * The [Dat2File] containing all cache data stored as [Sector]s. Dat2 files have the .dat2 file extension and are
 * encoded as an array of [Sector]s. Data is stored as a set of [Sector]s inside the [Dat2File]. The [Sector]s belonging
 * to can be stored non-sequentially.
 *
 * @property fileChannel The [FileChannel] to read the data from.
 */
internal class Dat2File private constructor(private val fileChannel: FileChannel) : AutoCloseable {
    /**
     * The size of the [Dat2File].
     */
    val size get() = fileChannel.size()

    /**
     * Reads data from the [Dat2File].
     */
    fun read(indexFileId: Int, containerId: Int, index: Index): ByteBuf {
        val totalData = Unpooled.compositeBuffer(
            ceil(index.dataSize.toDouble() / Sector.SIZE).toInt()
        )
        var sectorsRead = 0
        var dataToRead = index.dataSize
        var curSegByteStart = index.sectorNumber * Sector.SIZE.toLong()
        do {
            val dataSector = readSector(containerId, curSegByteStart)
            dataSector.validate(indexFileId, containerId, sectorsRead)
            if (dataToRead > dataSector.data.writerIndex()) {
                totalData.addComponent(true, dataSector.data)
                dataToRead -= dataSector.data.writerIndex()
                sectorsRead++
                curSegByteStart = dataSector.nextSectorNumber * Sector.SIZE.toLong()
            } else {
                totalData.addComponent(true, dataSector.data.slice(0, dataToRead))
                dataToRead = 0
            }
        } while (dataToRead > 0)
        return totalData
    }

    /**
     * Write data to the [Dat2File].
     */
    fun write(indexFileId: Int, containerId: Int, index: Index, data: ByteBuf) {
        val sectorDataSize = if(Sector.isExtended(containerId))  Sector.EXTENDED_DATA_SIZE else Sector.DATA_SIZE
        var sectorsWritten = 0
        var dataToWrite = index.dataSize
        var fileWriterIndex = index.sectorNumber.toLong() * Sector.SIZE.toLong()
        var prevSector: Sector? = null
        var prevWriterIndex: Long? = null
        while(dataToWrite > 0) {
            val containsData = containsData(fileWriterIndex)
            val sectorData = data.slice(
                sectorsWritten * sectorDataSize, if(dataToWrite < sectorDataSize) dataToWrite else sectorDataSize
            )
            val nextSector = Sector(containerId, sectorsWritten, 0, indexFileId, sectorData)
            if(containsData) {
                val curStoredSector = readSector(containerId, fileWriterIndex)
                if(curStoredSector.containerId != containerId || curStoredSector.indexFileId != indexFileId) {
                    fileWriterIndex = Sector.SIZE * ceil(size.toDouble() / Sector.SIZE).toLong()
                }
            }
            prevSector?.let { pSector ->
                pSector.nextSectorNumber = (fileWriterIndex / Sector.SIZE.toLong()).toInt()
                writeSector(pSector, prevWriterIndex!!)
            }
            prevSector = nextSector
            prevWriterIndex = fileWriterIndex
            fileWriterIndex += Sector.SIZE.toLong()
            sectorsWritten++
            dataToWrite -= nextSector.data.writerIndex()
        }
        if(prevSector != null && prevWriterIndex != null) {
            writeSector(prevSector, prevWriterIndex)
        }
    }

    /**
     * Checks if data exists at [pos].
     *
     * @param pos The position in the [Dat2File] to check.
     */
    private fun containsData(pos: Long) = pos < fileChannel.size()

    /**
     * Reads a [Sector] from the [fileChannel].
     *
     * @param byteStart The position to start reading.
     */
    private fun readSector(containerId: Int, byteStart: Long): Sector {
        val buf = Unpooled.buffer(Sector.SIZE)
        buf.writeBytes(fileChannel, byteStart, buf.writableBytes())
        return Sector.decode(containerId, buf)
    }

    /**
     * Writes a [Sector] to the [fileChannel].
     *
     * @param byteStart The position to start writing.
     */
    private fun writeSector(sector: Sector, byteStart: Long) {
        val sectorHeader = sector.encodeHeader() // write header separate to avoid copying
        sectorHeader.readBytes(fileChannel, byteStart, sectorHeader.readableBytes())
        sector.data.readBytes(fileChannel, byteStart + sectorHeader.readerIndex(), sector.data.readableBytes())
    }

    /**
     * Validates if the [Sector] data matches the expected data.
     *
     * @param indexFileId The expected index file id.
     * @param containerId The expected container id.
     * @param position The expected position compared to all other [Sector]s of the same [Js5Container] data.
     */
    private fun Sector.validate(indexFileId: Int, containerId: Int, position: Int): Sector {
        if (this.indexFileId != indexFileId) throw IOException(
            "Index id mismatch expected ${this.indexFileId} was $indexFileId."
        )
        if (this.containerId != containerId) throw IOException(
            "Js5Container id mismatch expected ${this.containerId} was $containerId."
        )
        if (this.position != position) throw IOException(
            "Sector position mismatch expected ${this.position} was $position."
        )
        return this
    }

    override fun close() = fileChannel.close()

    companion object {
        /**
         * The file extension.
         */
        const val EXTENSION = "dat2"

        fun open(path: Path): Dat2File {
            if (Files.exists(path)) {
                logger.debug { "Found .dat2 file" }
            } else throw FileNotFoundException(
                "Could not find .dat2 file at $path."
            )
            return Dat2File(FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE))
        }
    }
}

/**
 * A part of container data stored on disk.
 *
 * Container data is stored in multiple [Sector]s on disk. [Sector]s have a fixed size and have a header containing
 * meta-data about the [Sector] for validation. There are 2 types of [Sector]s: normal and extended.
 *
 * @property containerId The container id the [Sector] belongs to.
 * @property position The position of the [Sector] relative to the other [Sector]s of the same container.
 * @property nextSectorNumber The next [Sector] number after the current [Sector].
 * @property indexFileId The index file id the [Sector] belongs to.
 * @property data The data of the [Sector].
 */
public data class Sector(
    val containerId: Int,
    val position: Int,
    var nextSectorNumber: Int,
    val indexFileId: Int,
    val data: ByteBuf
) : DefaultByteBufHolder(data) {
    /**
     * Whether this [Sector] is extended.
     */
    public val isExtended: Boolean get() = isExtended(containerId)

    /**
     * Encodes the [Sector].
     */
    public fun encode(buf: ByteBuf = Unpooled.buffer(SIZE)): ByteBuf {
        if (isExtended) {
            buf.writeInt(containerId)
        } else {
            buf.writeShort(containerId)
        }
        buf.writeShort(position)
        buf.writeMedium(nextSectorNumber)
        buf.writeByte(indexFileId)
        buf.writeBytes(data)
        return buf
    }

    /**
     * Encodes the [Sector] without the data.
     */
    public fun encodeHeader(): ByteBuf = if (isExtended) {
        Unpooled.buffer(EXTENDED_HEADER_SIZE).writeInt(containerId)
    } else {
        Unpooled.buffer(HEADER_SIZE).writeShort(containerId)
    }.apply {
        writeShort(position)
        writeMedium(nextSectorNumber)
        writeByte(indexFileId)
    }

    override fun copy(): Sector = Sector(containerId, position, nextSectorNumber, indexFileId, data.copy())

    public companion object {
        /**
         * Size of the header in bytes for a normal [Sector].
         */
        public const val HEADER_SIZE: Int = 8

        /**
         * Size of the container data in bytes for a normal [Sector].
         */
        public const val DATA_SIZE: Int = 512

        /**
         * Size of the container data in bytes for an extended [Sector].
         */
        public const val EXTENDED_DATA_SIZE: Int = 510

        /**
         * Size of the header in bytes for an extended [Sector].
         */
        public const val EXTENDED_HEADER_SIZE: Int = 10

        /**
         * Total size of a [Sector].
         */
        public const val SIZE: Int = HEADER_SIZE + DATA_SIZE

        /**
         * Whether a [Sector] should be an extended [Sector].
         */
        public fun isExtended(containerId: Int): Boolean = containerId > 0xFFFF

        /**
         * Decodes a [Sector].
         */
        public fun decode(containerId: Int, data: ByteBuf): Sector = if (isExtended(containerId)) {
            decodeExtended(data)
        } else {
            decode(data)
        }

        /**
         * Decodes a normal [Sector].
         */
        private fun decode(buf: ByteBuf): Sector {
            val containerId = buf.readUnsignedShort()
            val position = buf.readUnsignedShort()
            val nextSectorNumber = buf.readUnsignedMedium()
            val indexFileId = buf.readUnsignedByte().toInt()
            val data = buf.slice(HEADER_SIZE, DATA_SIZE)
            return Sector(containerId, position, nextSectorNumber, indexFileId, data)
        }

        /**
         * Decodes an extended [Sector].
         */
        private fun decodeExtended(buf: ByteBuf): Sector {
            val containerId = buf.readInt()
            val position = buf.readUnsignedShort()
            val nextSectorNumber = buf.readUnsignedMedium()
            val indexFileId = buf.readUnsignedByte().toInt()
            val data = buf.slice(EXTENDED_HEADER_SIZE, EXTENDED_DATA_SIZE)
            return Sector(containerId, position, nextSectorNumber, indexFileId, data)
        }
    }
}
