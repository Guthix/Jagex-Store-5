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
package io.guthix.js5.container

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

/**
 * Data compression used in the [Js5Container] encoding.
 *
 * @property opcode The opcode to identify the compression type.
 * @property headerSize The size of the extra data required to use this compression type.
 */
public sealed class Js5Compression(public val opcode: Int, public val headerSize: Int) {
    /**
     * Compresses the data.
     */
    public abstract fun compress(input: ByteBuf): ByteBuf

    /**
     * Decompresses the data.
     *
     * @param length The expected uncompressed length.
     */
    public abstract fun decompress(input: ByteBuf, length: Int): ByteBuf

    public companion object {
        /**
         * Creates a new [Js5Compression] instance based on the [Js5Compression.opcode].
         */
        public fun getByOpcode(opcode: Int): Js5Compression = when (opcode) {
            0 -> Uncompressed()
            1 -> BZIP2()
            2 -> GZIP()
            3 -> LZMA()
            else -> throw IOException("Unsupported compression type $opcode")
        }
    }
}


public class Uncompressed : Js5Compression(opcode = 0, headerSize = 0) {
    override fun compress(input: ByteBuf): ByteBuf = input
    override fun decompress(input: ByteBuf, length: Int): ByteBuf = input.slice(input.readerIndex(), length)

    override fun equals(other: Any?): Boolean {
        if (other !is Uncompressed) return false
        return opcode == other.opcode
    }

    override fun hashCode(): Int = javaClass.hashCode()
}

public class BZIP2 : Js5Compression(opcode = 1, headerSize = Int.SIZE_BYTES) {
    override fun compress(input: ByteBuf): ByteBuf {
        ByteBufInputStream(input).use { inStream ->
            val bout = ByteBufOutputStream(Unpooled.buffer())
            BZip2CompressorOutputStream(bout, BLOCK_SIZE).use(inStream::transferTo)
            return@compress bout.buffer().slice(headerSize, bout.writtenBytes() - headerSize)
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

    override fun equals(other: Any?): Boolean {
        if (other !is BZIP2) return false
        return opcode == other.opcode
    }

    override fun hashCode(): Int = javaClass.hashCode()

    private companion object {
        private const val BLOCK_SIZE = 1

        private val HEADER = "BZh$BLOCK_SIZE".toByteArray(StandardCharsets.US_ASCII)
    }
}

public class GZIP : Js5Compression(opcode = 2, headerSize = Int.SIZE_BYTES) {
    override fun compress(input: ByteBuf): ByteBuf {
        ByteBufInputStream(input).use { inStream ->
            val bout = ByteBufOutputStream(Unpooled.buffer())
            GZIPOutputStream(bout).use(inStream::transferTo)
            return@compress bout.buffer()
        }
    }

    override fun decompress(input: ByteBuf, length: Int): ByteBuf {
        val decompressed = Unpooled.buffer(length)
        GZIPInputStream(ByteBufInputStream(input)).use { inStream ->
            while (inStream.available() == 1) decompressed.writeBytes(inStream, length)
        }
        return decompressed
    }

    override fun equals(other: Any?): Boolean {
        if (other !is GZIP) return false
        return opcode == other.opcode
    }

    override fun hashCode(): Int = javaClass.hashCode()
}

public class LZMA : Js5Compression(opcode = 3, headerSize = Int.SIZE_BYTES) {
    public lateinit var header: ByteBuf

    override fun compress(input: ByteBuf): ByteBuf {
        ByteBufOutputStream(Unpooled.buffer()).use { bout ->
            header.readBytes(bout, 5)
            ByteBufInputStream(input).use { inStream ->
                LZMAOutputStream(bout, LZMA2Options(), true).use(inStream::transferTo)
            }
            return@compress bout.buffer()
        }
    }

    override fun decompress(input: ByteBuf, length: Int): ByteBuf {
        val decompressed = Unpooled.buffer(length)
        header = input.slice(input.readerIndex(), 5)
        val propByte = input.readUnsignedByte()
        var dictionarySize = 0
        repeat(4) { dictionarySize += input.readUnsignedByte().toInt() shl (it * 8) }
        LZMAInputStream(ByteBufInputStream(input), -1, propByte.toByte(), dictionarySize).use { inStream ->
            decompressed.writeBytes(inStream, length)
        }
        return decompressed
    }

    override fun equals(other: Any?): Boolean {
        if (other !is LZMA) return false
        return opcode == other.opcode && header == other.header
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
