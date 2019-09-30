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
package io.guthix.cache.js5.container

import io.netty.buffer.ByteBuf
import io.netty.buffer.DefaultByteBufHolder
import io.netty.buffer.Unpooled
import java.io.IOException

/**
 * Reader and writer for [Js5Container]s.
 */
interface Js5ContainerReaderWriter : Js5ContainerReader, Js5ContainerWriter

/**
 * Reader for [Js5Container]s.
 */
interface Js5ContainerReader : AutoCloseable {
    /**
     * Amount of archives in this reader.
     */
    val archiveCount: Int

    /**
     * Reads raw container data from the cache.
     *
     * @param indexFileId The index to read from.
     * @param containerId The container to read from.
     */
    fun read(indexFileId: Int, containerId: Int): ByteBuf
}

/**
 * Writer for [Js5Container]s.
 */
interface Js5ContainerWriter : AutoCloseable {
    /**
     * Amount of archives in this writer.
     */
    val archiveCount: Int

    /***
     * Writes raw container data to the cache.
     *
     * @param indexFileId The index to write to.
     * @param containerId The container to write to.
     * @param data The data to write.
     */
    fun write(indexFileId: Int, containerId: Int, data: ByteBuf)
}

/**
 * An (Optional) encrypted and (Optional) compressed data volume that can be read from a cache.
 *
 * A [Js5Container] is the smallest piece of data that can be read and written from and to the cache.
 *
 * @property data The raw data of this [Js5Container].
 * @property xteaKey The XTEA key used to encrypt or decrypt this [Js5Container].
 * @property compression The compression used to compress or decompress this [Js5Container].
 * @property version (Optional) The version of this [Js5Container].
 */
data class Js5Container(
    var data: ByteBuf,
    var xteaKey: IntArray = XTEA_ZERO_KEY,
    var compression: Js5Compression = Uncompressed(),
    var version: Int? = null
) : DefaultByteBufHolder(data) {
    /**
     * Whether this [Js5Container] contains a version.
     */
    val isVersioned get() = version != null

    /**
     * Encodes the container into data that can be stored on the cache.
     */
    fun encode(): ByteBuf {
        val uncompressedSize = data.readableBytes()
        val compressedData = compression.compress(data)
        val compressedSize = compressedData.writerIndex()
        val totalHeaderSize = ENC_HEADER_SIZE + compression.headerSize
        val buf = Unpooled.buffer(
            totalHeaderSize + compressedSize + if(version != null) Short.SIZE_BYTES else 0
        )
        buf.writeByte(compression.opcode)
        buf.writeInt(compressedSize)
        if(compression !is Uncompressed) buf.writeInt(uncompressedSize)
        buf.writeBytes(compressedData)
        buf.writeShort(version ?: -1)
        return if(!xteaKey.isZeroKey()) {
            buf.xteaEncrypt(xteaKey, start = ENC_HEADER_SIZE, end = totalHeaderSize + compressedSize)
        } else buf
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false
        other as Js5Container
        if (data != other.data) return false
        if (compression != other.compression) return false
        if (!xteaKey.contentEquals(other.xteaKey)) return false
        if (version != other.version) return false
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + compression.hashCode()
        result = 31 * result + xteaKey.contentHashCode()
        result = 31 * result + (version ?: 0)
        return result
    }

    /**
     * The [compressed] and [uncompressed] sizes of a [Js5Container].
     *
     * @property compressed The compressed size of a [Js5Container].
     * @property uncompressed The uncompressed size of a [Js5Container].
     */
    data class Size(var compressed: Int, var uncompressed: Int)

    companion object {
        /**
         * Amount of bytes before encryption starts.
         */
        const val ENC_HEADER_SIZE = Byte.SIZE_BYTES + Int.SIZE_BYTES

        private fun IntArray.isZeroKey() = this.contentEquals(XTEA_ZERO_KEY)

        /**
         * Decodes the [Js5Container].
         *
         * @param data The data to decode.
         * @param xteaKey (Optional) The XTEA encryption key to decrypt the [data].
         */
        fun decode(data: ByteBuf, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Container {
            val compression = Js5Compression.getByOpcode(data.readUnsignedByte().toInt())
            val compressedSize = data.readInt()
            val totalHeaderSize = ENC_HEADER_SIZE + compression.headerSize
            val indexAfterCompression = totalHeaderSize + compressedSize
            if(!xteaKey.isZeroKey()) {
                data.xteaDecrypt(xteaKey, start = ENC_HEADER_SIZE, end = indexAfterCompression)
            }
            val dataBuffer = if(compression !is Uncompressed) {
                val uncompressedSize = data.readInt()
                val uncompressed = compression.decompress(data, uncompressedSize)
                if (uncompressed.writerIndex() != uncompressedSize) throw IOException(
                    "Decompressed size was ${uncompressed.writerIndex()} but expected $uncompressedSize."
                )
                Unpooled.wrappedBuffer(uncompressed)
            } else data.slice(ENC_HEADER_SIZE, compressedSize)
            data.readerIndex(indexAfterCompression)
            val version = if(data.readableBytes() >= 2) data.readShort().toInt() else null
            return Js5Container(dataBuffer, xteaKey, compression, version)
        }

        fun decodeSize(data: ByteBuf, xteaKey: IntArray = XTEA_ZERO_KEY): Size {
            val compression = Js5Compression.getByOpcode(data.readUnsignedByte().toInt())
            val compressedSize = data.readInt()
            if(compression is Uncompressed) return Size(compressedSize, compressedSize)
            val totalHeaderSize = ENC_HEADER_SIZE + compression.headerSize
            val indexAfterCompression = totalHeaderSize + compressedSize
            if(!xteaKey.isZeroKey()) {
                data.xteaDecrypt(xteaKey, start = ENC_HEADER_SIZE, end = indexAfterCompression)
            }
            val uncompressedSize = data.readInt()
            return Size(compressedSize, uncompressedSize)
        }
    }
}
