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
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.LZMAOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class Js5Compression(val opcode: Int, val headerSize: Int) {
    abstract fun compress(input: ByteBuf): ByteBuf

    abstract fun decompress(input: ByteBuf, length: Int): ByteBuf

    companion object {
        fun getByOpcode(opcode: Int): Js5Compression = when(opcode) {
            0 -> Uncompressed()
            1 -> BZIP2()
            2 -> GZIP()
            3 -> LZMA()
            else -> throw IOException("Unsupported compression type $opcode")
        }
    }
}

class Uncompressed : Js5Compression(opcode = 0, headerSize = 0) {
    override fun compress(input: ByteBuf) = input
    override fun decompress(input: ByteBuf, length: Int) = input.slice(input.readerIndex(), length)
}

class BZIP2 : Js5Compression(opcode = 1, headerSize = Int.SIZE_BYTES) {
    override fun compress(input: ByteBuf): ByteBuf {
        ByteBufInputStream(input).use { inStream ->
            val bout = ByteBufOutputStream(Unpooled.buffer())
            BZip2CompressorOutputStream(bout, BLOCK_SIZE).use { outStream ->
                inStream.transferTo(outStream)
            }
            return bout.buffer().slice(headerSize, bout.writtenBytes() - headerSize)
        }
    }

    override fun decompress(input: ByteBuf, length: Int): ByteBuf {
        val decompressed = Unpooled.buffer(length)
        val str = SequenceInputStream(ByteArrayInputStream(HEADER), ByteBufInputStream(input))
        BZip2CompressorInputStream(str).use { inStream ->
            decompressed.writeBytes(inStream, length)
        }
        return decompressed
    }

    companion object {
        private val BLOCK_SIZE = 1

        private val HEADER = "BZh$BLOCK_SIZE".toByteArray(StandardCharsets.US_ASCII)
    }
}

class GZIP : Js5Compression(opcode = 2, headerSize = Int.SIZE_BYTES) {
    override fun compress(input: ByteBuf): ByteBuf {
        ByteBufInputStream(input).use { inStream ->
            val bout = ByteBufOutputStream(Unpooled.buffer())
            GZIPOutputStream(bout).use { outStream ->
                inStream.transferTo(outStream)
            }
            return bout.buffer()
        }
    }

    override fun decompress(input: ByteBuf, length: Int): ByteBuf {
        val decompressed = Unpooled.buffer(length)
        GZIPInputStream(ByteBufInputStream(input)).use { inStream ->
            while(inStream.available() == 1) decompressed.writeBytes(inStream, length)
        }
        return decompressed
    }
}

class LZMA : Js5Compression(opcode = 3, headerSize = Int.SIZE_BYTES) {
    lateinit var header: ByteBuf

    override fun compress(input: ByteBuf): ByteBuf {
        ByteBufOutputStream(Unpooled.buffer()).use { bout ->
            header.readBytes(bout, 5)
            ByteBufInputStream(input).use { inStream ->
                LZMAOutputStream(bout, LZMA2Options(), true).use { outStream ->
                    inStream.transferTo(outStream)
                }
            }
            return bout.buffer()
        }
    }

    override fun decompress(input: ByteBuf, length: Int): ByteBuf {
        val decompressed = Unpooled.buffer(length)
        header = input.slice(input.readerIndex(), 5)
        val propByte = input.readByte()
        var dictionarySize = 0
        repeat(4) { dictionarySize += input.readUnsignedByte().toInt() shl (it * 8) }
        LZMAInputStream(ByteBufInputStream(input), -1, propByte, dictionarySize).use { inStream ->
            decompressed.writeBytes(inStream, length)
        }
        return decompressed
    }
}
