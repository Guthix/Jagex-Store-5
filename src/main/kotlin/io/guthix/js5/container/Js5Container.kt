/*
 * Copyright 2018-2020 Guthix
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
@file:Suppress("unused", "DuplicatedCode")

package io.guthix.js5.container

import io.guthix.js5.util.XTEA_ZERO_KEY
import io.guthix.js5.util.xteaDecrypt
import io.guthix.js5.util.xteaEncrypt
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
public data class Js5Container(
    var data: ByteBuf,
    var xteaKey: IntArray = XTEA_ZERO_KEY,
    var compression: Js5Compression = Uncompressed,
    var version: Int? = null
) : DefaultByteBufHolder(data) {
    /**
     * Whether this [Js5Container] contains a version.
     */
    public val isVersioned: Boolean get() = version != null

    /**
     * Encodes the container into data that can be stored on the cache.
     */
    public fun encode(): ByteBuf {
        val uncompressedSize = data.readableBytes()
        val compressedData = compression.compress(data)
        val compressedSize = compressedData.writerIndex()
        val totalHeaderSize = ENC_HEADER_SIZE + compression.headerSize
        val buf = Unpooled.buffer(
            totalHeaderSize + compressedSize + if (version != null) Short.SIZE_BYTES else 0
        )
        buf.writeByte(compression.opcode)
        buf.writeInt(compressedSize)
        if (compression !is Uncompressed) buf.writeInt(uncompressedSize)
        buf.writeBytes(compressedData)
        version?.let(buf::writeShort)
        return if (!xteaKey.isZeroKey) {
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
    public data class Size(var compressed: Int, var uncompressed: Int)

    public companion object {
        /**
         * Amount of bytes before encryption starts.
         */
        public const val ENC_HEADER_SIZE: Int = Byte.SIZE_BYTES + Int.SIZE_BYTES

        private val IntArray.isZeroKey get() = contentEquals(XTEA_ZERO_KEY)

        /**
         * Decodes the [Js5Container].
         */
        public fun decode(buf: ByteBuf, xteaKey: IntArray = XTEA_ZERO_KEY): Js5Container {
            val compression = Js5Compression.getByOpcode(buf.readUnsignedByte().toInt())
            val compressedSize = buf.readInt()
            val encComprSize = compression.headerSize + compressedSize
            val decBuf = if (!xteaKey.isZeroKey) {
                buf.xteaDecrypt(xteaKey, end = buf.readerIndex() + encComprSize)
            } else buf.slice(buf.readerIndex(), buf.readableBytes())
            val decompBuf = if (compression !is Uncompressed) {
                val uncompressedSize = decBuf.readInt()
                val uncompressed = compression.decompress(decBuf, uncompressedSize)
                if (uncompressed.writerIndex() != uncompressedSize) throw IOException(
                    "Decompressed size was ${uncompressed.writerIndex()} but expected $uncompressedSize."
                )
                Unpooled.wrappedBuffer(uncompressed)
            } else decBuf.slice(0, compressedSize)
            decBuf.readerIndex(encComprSize)
            val version = if (decBuf.readableBytes() >= 2) decBuf.readShort().toInt() else null
            return Js5Container(decompBuf, xteaKey, compression, version)
        }

        /**
         * Returns the version of an encoded [Js5Container].
         *
         * This method is more efficient than calling [decode] and taking the version of the [Js5Container] object.
         */
        public fun decodeVersion(buf: ByteBuf): Int? {
            val compression = Js5Compression.getByOpcode(buf.readUnsignedByte().toInt())
            val compressedSize = buf.readInt()
            val totalSize = ENC_HEADER_SIZE + compression.headerSize + compressedSize
            buf.readerIndex(totalSize)
            return if (buf.readableBytes() >= 2) buf.readShort().toInt() else null
        }

        /**
         * Returns the version of an encoded [Js5Container.Size].
         */
        public fun decodeSize(buf: ByteBuf, xteaKey: IntArray = XTEA_ZERO_KEY): Size {
            val compression = Js5Compression.getByOpcode(buf.readUnsignedByte().toInt())
            val compressedSize = buf.readInt()
            if (compression is Uncompressed) return Size(compressedSize, compressedSize)
            val encComprSize = compression.headerSize + compressedSize
            val decBuf = if (!xteaKey.isZeroKey) {
                buf.xteaDecrypt(xteaKey, end = buf.readerIndex() + encComprSize)
            } else buf.slice(buf.readerIndex(), buf.readableBytes())
            val uncompressedSize = decBuf.readInt()
            return Size(compressedSize, uncompressedSize)
        }
    }
}
