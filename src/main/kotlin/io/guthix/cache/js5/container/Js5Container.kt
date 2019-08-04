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

import io.guthix.cache.js5.io.uByte
import io.guthix.cache.js5.util.*
import io.guthix.cache.js5.util.xteaDecrypt
import io.guthix.cache.js5.util.xteaEncrypt
import java.io.IOException
import java.nio.ByteBuffer

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
    fun read(indexFileId: Int, containerId: Int): ByteArray
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
    fun write(indexFileId: Int, containerId: Int, data: ByteArray)
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
data class Js5Container(var version: Int = -1, val data: ByteArray) {
    /**
     * Encodes the container into data that can be stored on the cache.
     *
     * @param js5Compression (Optional) The compression type to use.
     * @param xteaKey (Optional) The XTEA key to encrypt the container.
     */
    fun encode(js5Compression: Js5Compression = Js5Compression.NONE, xteaKey: IntArray = XTEA_ZERO_KEY): ByteArray {
        require(xteaKey.size == XTEA_KEY_SIZE)
        val compressedData = js5Compression.compress(data)
        val buffer = ByteBuffer.allocate(
            ENC_HEADER_SIZE + js5Compression.headerSize + compressedData.size + if(isVersioned) 2 else 0
        )
        buffer.put(js5Compression.opcode.toByte())
        buffer.putInt(compressedData.size)
        if(js5Compression != Js5Compression.NONE) buffer.putInt(data.size)
        buffer.put(compressedData)
        if(isVersioned) buffer.putShort(version.toShort())
        return if(xteaKey.all { it != 0 }) {
            buffer.xteaEncrypt(
                key = xteaKey,
                start = ENC_HEADER_SIZE,
                end = ENC_HEADER_SIZE + js5Compression.headerSize + compressedData.size
            )
        } else buffer.array()
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Js5Container

        if (version != other.version) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + data.contentHashCode()
        return result
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
        fun decode(data: ByteArray, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Container {
            val buffer = ByteBuffer.wrap(data)
            require(xteaKey.size == XTEA_KEY_SIZE)
            val compression = Js5Compression.getByOpcode(buffer.uByte.toInt())
            val compressedSize = buffer.int
            if(xteaKey.all { it != 0 }) {
                buffer.xteaDecrypt(
                    key = xteaKey,
                    start = ENC_HEADER_SIZE,
                    end = ENC_HEADER_SIZE + compression.headerSize + compressedSize
                )
            }
            val dataBuffer = if(compression != Js5Compression.NONE) {
                val uncompressedSize = buffer.int
                val headerLength = ENC_HEADER_SIZE + compression.headerSize
                val uncompressed = compression.decompress(
                    buffer.array().sliceArray(headerLength until headerLength + compressedSize), uncompressedSize
                )
                if (uncompressed.size != uncompressedSize) throw IOException("Compression size mismatch.")
                uncompressed
            } else buffer.array().sliceArray(ENC_HEADER_SIZE until ENC_HEADER_SIZE + compressedSize)
            buffer.position(ENC_HEADER_SIZE + compression.headerSize + compressedSize)
            val version = if(buffer.remaining() >= 2) buffer.short.toInt() else -1
            return Js5Container(version, dataBuffer)
        }
    }
}
