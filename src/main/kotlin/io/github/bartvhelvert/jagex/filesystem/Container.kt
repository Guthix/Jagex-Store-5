package io.github.bartvhelvert.jagex.filesystem

import java.io.IOException
import java.nio.ByteBuffer

internal data class Container(val version: Int, val data: ByteBuffer) {
    companion object {
        @ExperimentalUnsignedTypes
        internal fun decode(buffer: ByteBuffer, xteaKey: IntArray = XTEA.ZERO_KEY): Container {
            require(xteaKey.size == XTEA.KEY_SIZE)
            val compression = Compression.getByOpcode(buffer.uByte.toInt())
            val compressedSize = buffer.int
            val compressionBuffer = buffer.slice().decipher(compression, compressedSize, xteaKey)
            val dataBuffer = compressionBuffer.decompress(compression, compressedSize)
            val version = compressionBuffer.decodeVersion()
            return Container(version, dataBuffer)
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