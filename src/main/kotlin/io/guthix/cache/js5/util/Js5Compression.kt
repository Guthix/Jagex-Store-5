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
package io.guthix.cache.js5.util

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import io.guthix.cache.js5.container.Js5Container

/**
 * Various compression algorithms used in the [Js5Container] encoding.
 *
 * @property opcode The opcode used to encode the compression.
 * @property headerSize The header size for using the compression type.
 */
enum class Js5Compression(val opcode: UByte, val headerSize: Int) {
    NONE(opcode = 0u, headerSize = 0) {
        override fun compress(input: ByteArray) = input
        override fun decompress(input: ByteArray, decompressedSize: Int) = input
    },

    BZIP2(opcode = 1u, headerSize = Int.SIZE_BYTES) {
        val blockSize = 1

        val header = "BZh$blockSize".toByteArray(StandardCharsets.US_ASCII)

        override fun compress(input: ByteArray): ByteArray {
            ByteArrayInputStream(input).use { inStream ->
                val bout = ByteArrayOutputStream()
                BZip2CompressorOutputStream(bout, blockSize).use { outStream ->
                    inStream.transferTo(outStream)
                }
                return bout.toByteArray().sliceArray(header.size until bout.size())
            }
        }

        override fun decompress(input: ByteArray, decompressedSize: Int): ByteArray {
            val decompressed = ByteArray(decompressedSize)
            val str = SequenceInputStream(ByteArrayInputStream(header), ByteArrayInputStream(input))
            BZip2CompressorInputStream(str).use { inStream ->
                inStream.readNBytes(decompressed, 0, decompressed.size)
            }
            return decompressed
        }
    },

    GZIP(opcode = 2u, headerSize = Int.SIZE_BYTES) {
        override fun compress(input: ByteArray): ByteArray {
            ByteArrayInputStream(input).use { inStream ->
                val bout = ByteArrayOutputStream()
                GZIPOutputStream(bout).use { outStream ->
                    inStream.transferTo(outStream)
                }
                return bout.toByteArray()
            }
        }

        override fun decompress(input: ByteArray, decompressedSize: Int): ByteArray {
            val decompressed = ByteArray(decompressedSize)
            GZIPInputStream(ByteArrayInputStream(input)).use { inStream ->
                inStream.readNBytes(decompressed, 0, decompressed.size)
            }
            return decompressed
        }
    },

    LZMA(opcode = 3u, headerSize = Int.SIZE_BYTES) {
        override fun compress(input: ByteArray): ByteArray {
            ByteArrayInputStream(input).use { inStream ->
                val bout = ByteArrayOutputStream()
                LZMACompressorOutputStream(bout).use { outStream ->
                    inStream.transferTo(outStream)
                }
                return bout.toByteArray()
            }
        }

        override fun decompress(input: ByteArray, decompressedSize: Int): ByteArray {
            val decompressed = ByteArray(decompressedSize)
            LZMACompressorInputStream(ByteArrayInputStream(input)).use { inStream ->
                inStream.readNBytes(decompressed, 0, decompressed.size)
            }
            return decompressed
        }
    };

    /**
     * Compresses a [ByteArray].
     */
    abstract fun compress(input: ByteArray): ByteArray

    /**
     * Decompresses a [ByteArray].
     */
    abstract fun decompress(input: ByteArray, decompressedSize: Int): ByteArray

    companion object {
        /**
         * Gets the compression type by the [opcode].
         */
        fun getByOpcode(opcode: Int) = values().first{ opcode == it.opcode.toInt() }
    }
}
