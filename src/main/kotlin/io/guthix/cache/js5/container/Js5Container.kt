/**
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
@file:Suppress("unused", "DuplicatedCode")
package io.guthix.cache.js5.container

import io.guthix.cache.js5.util.XTEA_ZERO_KEY
import io.guthix.cache.js5.util.xteaDecrypt
import io.guthix.cache.js5.util.xteaEncrypt
import io.netty.buffer.ByteBuf
import io.netty.buffer.DefaultByteBufHolder
import io.netty.buffer.Unpooled
import java.io.IOException

/**
 * An (Optional) encrypted and (Optional) compressed data volume that can be read from a cache. A [Js5Container] handles
 * the encryption and compression of cache files. A [Js5Container] can optionally contain a version to check if the
 * container is up to date.
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
        version?.let { buf.writeShort(it) }
        return if(!xteaKey.isZeroKey) {
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
     * @property compressed The compressed size of the [Js5Container] without the version.
     * @property uncompressed The uncompressed size of the data of the [Js5Container].
     */
    data class Size(var compressed: Int, var uncompressed: Int)

    companion object {
        /**
         * Amount of bytes before encryption starts.
         */
        const val ENC_HEADER_SIZE = Byte.SIZE_BYTES + Int.SIZE_BYTES

        private val IntArray.isZeroKey get() = contentEquals(XTEA_ZERO_KEY)

        /**
         * Decodes the [Js5Container].
         */
        fun decode(buf: ByteBuf, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Container {
            val compression = Js5Compression.getByOpcode(buf.readUnsignedByte().toInt())
            val compressedSize = buf.readInt()
            val totalHeaderSize = ENC_HEADER_SIZE + compression.headerSize
            val indexAfterCompression = totalHeaderSize + compressedSize
            if(!xteaKey.isZeroKey) {
                buf.xteaDecrypt(xteaKey, start = ENC_HEADER_SIZE, end = indexAfterCompression)
            }
            val decompBuf = if(compression !is Uncompressed) {
                val uncompressedSize = buf.readInt()
                val uncompressed = compression.decompress(buf, uncompressedSize)
                if (uncompressed.writerIndex() != uncompressedSize) throw IOException(
                    "Decompressed size was ${uncompressed.writerIndex()} but expected $uncompressedSize."
                )
                Unpooled.wrappedBuffer(uncompressed)
            } else buf.slice(ENC_HEADER_SIZE, compressedSize)
            buf.readerIndex(indexAfterCompression)
            val version = if(buf.readableBytes() >= 2) buf.readShort().toInt() else null
            return Js5Container(decompBuf, xteaKey, compression, version)
        }

        /**
         * Decodes the [Js5Container] and returns the [Size] of the container.
         */
        fun decodeSize(data: ByteBuf, xteaKey: IntArray = XTEA_ZERO_KEY): Size {
            val compression = Js5Compression.getByOpcode(data.readUnsignedByte().toInt())
            val compressedSize = data.readInt()
            if(compression is Uncompressed) return Size(compressedSize, compressedSize)
            val totalHeaderSize = ENC_HEADER_SIZE + compression.headerSize
            val indexAfterCompression = totalHeaderSize + compressedSize
            if(!xteaKey.isZeroKey) {
                data.xteaDecrypt(xteaKey, start = ENC_HEADER_SIZE, end = indexAfterCompression)
            }
            val uncompressedSize = data.readInt()
            return Size(compressedSize, uncompressedSize)
        }
    }
}
