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

import io.guthix.cache.js5.Js5GroupSettings
import io.guthix.cache.js5.util.*
import io.guthix.cache.js5.util.xteaEncrypt
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
 * A [Js5Container] is the smallest piece of data that can be read from the cache. A container can (Optionally) contain
 * a version.
 *
 * @property version The version of this [Js5Container].
 * @property data The data of this [Js5Container].
 */
data class Js5Container(var version: Int = -1, val data: ByteBuf) : DefaultByteBufHolder(data) {
    /**
     * Encodes the container into data that can be stored on the cache.
     *
     * @param js5Compression (Optional) The compression type to use.
     * @param xteaKey (Optional) The XTEA key to encrypt the container.
     */
    fun encode(js5Compression: Js5Compression = Js5Compression.NONE, xteaKey: IntArray = XTEA_ZERO_KEY): ByteBuf {
        val compressedData = js5Compression.compress(data.array())
        val buf = Unpooled.buffer(
            ENC_HEADER_SIZE + js5Compression.headerSize + compressedData.size + if(isVersioned) 2 else 0
        )
        buf.writeByte(js5Compression.opcode)
        buf.writeInt(compressedData.size)
        if(js5Compression != Js5Compression.NONE) buf.writeInt(data.readableBytes())
        buf.writeBytes(compressedData)
        if(isVersioned) buf.writeShort(version)
        return if(xteaKey.all { it != 0 }) {
            buf.xteaEncrypt(
                key = xteaKey,
                start = ENC_HEADER_SIZE,
                end = ENC_HEADER_SIZE + js5Compression.headerSize + compressedData.size
            )
        } else buf
    }

    /**
     * Returns whether this container contains a version.
     */
    val isVersioned get() = version != -1

    /**
     * Removes the version from this container.
     */
    fun removeVersion() {
        version = -1
    }

    companion object {
        /**
         * Amount of bytes before encryption starts.
         */
        const val ENC_HEADER_SIZE = Int.SIZE_BYTES + Byte.SIZE_BYTES

        /**
         * Decodes the [Js5Container].
         *
         * @param data The data to decode.
         * @param xteaKey (Optional) The XTEA encryption key to decrypt the [data].
         */
        fun decode(data: ByteBuf, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Container {
            val compression = Js5Compression.getByOpcode(data.readUnsignedByte().toInt())
            val compressedSize = data.readInt()
            if(xteaKey.all { it != 0 }) {
                data.xteaDecrypt(
                    key = xteaKey,
                    start = ENC_HEADER_SIZE,
                    end = ENC_HEADER_SIZE + compression.headerSize + compressedSize
                )
            }
            val dataBuffer = if(compression != Js5Compression.NONE) {
                val uncompressedSize = data.readInt()
                val headerLength = ENC_HEADER_SIZE + compression.headerSize
                val uncompressed = compression.decompress(
                    data.array().sliceArray(headerLength until headerLength + compressedSize), uncompressedSize
                )
                if (uncompressed.size != uncompressedSize) throw IOException("Compression size mismatch.")
                Unpooled.wrappedBuffer(uncompressed)
            } else data.slice(ENC_HEADER_SIZE, compressedSize)
            data.readerIndex(ENC_HEADER_SIZE + compression.headerSize + compressedSize)
            val version = if(data.readableBytes() >= 2) data.readShort().toInt() else -1
            return Js5Container(version, dataBuffer)
        }

        /**
         * Decodes a container and gets the size.
         *
         * @param data The data to decode.
         * @param xteaKey (Optional) The XTEA encryption key to decrypt the [data].
         */
        fun sizeOf(data: ByteBuf, xteaKey: IntArray = XTEA_ZERO_KEY): Js5GroupSettings.Size {
            val compression = Js5Compression.getByOpcode(data.readUnsignedByte().toInt())
            val compressedSize = data.readInt()
            if(xteaKey.all { it != 0 }) {
                data.xteaDecrypt(
                    key = xteaKey,
                    start = ENC_HEADER_SIZE,
                    end = ENC_HEADER_SIZE + compression.headerSize + compressedSize
                )
            }
            return if(compression != Js5Compression.NONE) {
                Js5GroupSettings.Size(compressedSize, data.readableBytes())
            } else {
                Js5GroupSettings.Size(compressedSize, compressedSize)
            }
        }
    }
}
