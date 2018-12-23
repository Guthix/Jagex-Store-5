package io.github.bartvhelvert.jagex.filesystem.transform

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class Compression(val opcode: Byte, val headerSize: Int) {
    NONE(opcode = 0, headerSize = 0) {
        override fun compress(input: ByteArray) = input
        override fun decompress(input: ByteArray, decompressedSize: Int) = input
    },


    BZIP2(opcode = 1, headerSize = Int.SIZE_BYTES) {
        val BLOCK_SIZE = 1

        val HEADER = "BZh$BLOCK_SIZE".toByteArray(StandardCharsets.US_ASCII)

        override fun compress(input: ByteArray): ByteArray {
            ByteArrayInputStream(input).use { inStream ->
                val bout = ByteArrayOutputStream()
                BZip2CompressorOutputStream(bout, BLOCK_SIZE).use { outStream ->
                    inStream.transferTo(outStream)
                }
                return bout.toByteArray()
            }
        }

        override fun decompress(input: ByteArray, decompressedSize: Int): ByteArray {
            val decompressed = ByteArray(decompressedSize)
            val str = SequenceInputStream(ByteArrayInputStream(HEADER), ByteArrayInputStream(input))
            BZip2CompressorInputStream(str).use { inStream ->
                inStream.readNBytes(decompressed, 0, decompressed.size)
            }
            return decompressed
        }
    },

    GZIP(opcode = 2, headerSize = Int.SIZE_BYTES) {
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
    };

    abstract fun compress(input: ByteArray): ByteArray

    abstract fun decompress(input: ByteArray, decompressedSize: Int): ByteArray

    companion object {
        fun getByOpcode(opcode: Int) = Compression.values().first{ opcode == it.opcode.toInt() }
    }
}