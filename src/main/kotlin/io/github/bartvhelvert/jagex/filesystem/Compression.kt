package io.github.bartvhelvert.jagex.filesystem

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class Compression(val opcode: Int, val headerSize: Int) {
    NONE(opcode = 0, headerSize = 0) {
        override fun compress(input: ByteArray) = input
        override fun decompress(input: ByteArray) = input
    },


    BZIP2(opcode = 1, headerSize = Int.SIZE_BYTES) {
        val BLOCK_SIZE = 1

        val HEADER_SIZE = 2

        override fun compress(input: ByteArray): ByteArray {
            ByteArrayInputStream(input).use { inStream ->
                val bout = ByteArrayOutputStream()
                BZip2CompressorOutputStream(bout, BLOCK_SIZE).use { outStream ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var len = inStream.read(buf, 0, buf.size)
                    while (len != -1) {
                        outStream.write(buf, 0, len)
                        len = inStream.read(buf, 0, buf.size)
                    }
                }
                val compressed = bout.toByteArray()
                return compressed.sliceArray(HEADER_SIZE..compressed.size)
            }
        }

        override fun decompress(input: ByteArray): ByteArray {
            val bzip2 = ByteArray(input.size + HEADER_SIZE)
            bzip2[0] = 'h'.toByte()
            bzip2[1] = '1'.toByte()
            System.arraycopy(input, 0, bzip2, HEADER_SIZE, input.size)
            BZip2CompressorInputStream(ByteArrayInputStream(bzip2)).use { inStream ->
                val outputStream = ByteArrayOutputStream()
                outputStream.use { outStream ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var len = inStream.read(buf, 0, buf.size)
                    while (len != -1) {
                        outStream.write(buf, 0, len)
                        len = inStream.read(buf, 0, buf.size)
                    }
                }
                return outputStream.toByteArray()
            }
        }
    },

    GZIP(opcode = 2, headerSize = Int.SIZE_BYTES) {
        override fun compress(input: ByteArray): ByteArray {
            val inputStream = ByteArrayInputStream(input)
            inputStream.use { inStream ->
                val bout = ByteArrayOutputStream()
                val outputStream = GZIPOutputStream(bout)
                outputStream.use { outStream ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var len = inStream.read(buf, 0, buf.size)
                    while (len != -1) {
                        outStream.write(buf, 0, len)
                        len = inStream.read(buf, 0, buf.size)
                    }
                }
                return bout.toByteArray()
            }
        }

        override fun decompress(input: ByteArray): ByteArray {
            val inputStream = GZIPInputStream(ByteArrayInputStream(input))
            inputStream.use { inStream ->
                val outputStream = ByteArrayOutputStream()
                outputStream.use { outStream ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var len = inStream.read(buf, 0, buf.size)
                    while (len!= -1) {
                        outStream.write(buf, 0, len)
                        len = inStream.read(buf, 0, buf.size)
                    }
                }
                return outputStream.toByteArray()
            }
        }
    };

    abstract fun compress(input: ByteArray): ByteArray

    abstract fun decompress(input: ByteArray): ByteArray

    companion object {
        const val BUFFER_SIZE = 4096

        fun getByOpcode(opcode: Int) = Compression.values().first{ opcode == it.opcode }
    }
}