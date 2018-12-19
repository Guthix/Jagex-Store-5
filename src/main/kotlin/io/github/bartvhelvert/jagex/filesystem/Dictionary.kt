package io.github.bartvhelvert.jagex.filesystem

import java.io.IOException
import java.nio.ByteBuffer

class Dictionary(val version: Int, val entries: Array<ByteBuffer>) {
    companion object {
        @ExperimentalUnsignedTypes
        internal fun decode(encodedDictionary: EncodedDictionary, fileCount: Int): Dictionary {
            val buffer = encodedDictionary.data
            val fileSizes = IntArray(fileCount)
            val chunkCount = buffer.getUByte(buffer.limit() - 1).toInt()
            val chunkSizes = Array(chunkCount) { IntArray(fileCount) }
            buffer.position(buffer.limit() - 1 - chunkCount * fileCount * 4)
            for (chunkId in 0 until chunkCount) {
                var chunkSize = 0
                for (fileId in 0 until fileCount) {
                    val delta = buffer.int // difference in chunk size compared to the previous chunk
                    chunkSize += delta
                    chunkSizes[chunkId][fileId] = chunkSize
                    fileSizes[fileId] += chunkSize
                }
            }
            val fileData = Array<ByteBuffer>(fileCount) {
                ByteBuffer.allocate(fileSizes[it])
            }
            buffer.position(0)
            for (chunkId in 0 until chunkCount) {
                for (fileId in 0 until fileCount) {
                    val chunkSize = chunkSizes[chunkId][fileId]
                    val temp = ByteArray(chunkSize)
                    buffer.get(temp)
                    fileData[fileId].put(temp)
                }
            }
            return Dictionary(encodedDictionary.version, fileData)
        }

        @ExperimentalUnsignedTypes
        internal fun decipherAndDecompress(
            buffer: ByteBuffer,
            xteaKey: IntArray = XTEA.ZERO_KEY
        ): EncodedDictionary {
            val compression = Compression.getByOpcode(buffer.uByte.toInt())
            val compressedSize = buffer.int
            val compressionBuffer = buffer.slice().decipher(compression, compressedSize, xteaKey)
            val dataBuffer = compressionBuffer.decompress(compression, compressedSize)
            val version = compressionBuffer.decodeVersion()
            return EncodedDictionary(version, dataBuffer)
        }

        private fun ByteBuffer.decipher(compression: Compression, compressedSize: Int, xteaKey: IntArray) =
            if(xteaKey.all { it != 0 }) {
                this
            } else {
                xteaDecrypt(xteaKey, end = compression.headerSize + compressedSize)
            }

        private fun ByteBuffer.decompress(compression: Compression, compressedSize: Int): ByteBuffer {
            if(compression == Compression.NONE) return this
            val uncompressedSize = int
            val uncompressed = compression.decompress(array().sliceArray(0..compressedSize))
            if (uncompressed.size != uncompressedSize) throw IOException("Compression size mismatch")
            return ByteBuffer.wrap(uncompressed)
        }

        private fun ByteBuffer.decodeVersion() = if (remaining() >= 2) {
            short.toInt()
        } else {
            -1
        }
    }
}

internal class EncodedDictionary(val version: Int, val data: ByteBuffer)