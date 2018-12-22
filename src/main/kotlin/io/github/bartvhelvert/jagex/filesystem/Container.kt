package io.github.bartvhelvert.jagex.filesystem

import java.io.IOException
import java.nio.ByteBuffer

internal data class Container(val version: Int, val data: ByteBuffer) {
    companion object {
        const val ENC_HEADER_SIZE = Byte.SIZE_BYTES + Int.SIZE_BYTES

        const val COMP_HEADER_SIZE = ENC_HEADER_SIZE + Int.SIZE_BYTES

        @ExperimentalUnsignedTypes
        internal fun decode(buffer: ByteBuffer, xteaKey: IntArray = XTEA.ZERO_KEY): Container {
            require(xteaKey.size == XTEA.KEY_SIZE)
            val compression = Compression.getByOpcode(buffer.uByte.toInt())
            val compressedSize = buffer.int
            buffer.decrypt(xteaKey, compression, compressedSize)
            val dataBuffer = buffer.decompress(compression, compressedSize)
            buffer.position(COMP_HEADER_SIZE + compressedSize)
            val version = buffer.decodeVersion()
            return Container(version, dataBuffer)
        }

        private fun ByteBuffer.decrypt(xteaKeys: IntArray, compression: Compression, compressedSize: Int) =
            if (xteaKeys.all { it == 0 }) {
                this
            } else {
                xteaDecrypt(xteaKeys, ENC_HEADER_SIZE, ENC_HEADER_SIZE + compression.headerSize + compressedSize)
            }

        private fun ByteBuffer.decompress(compression: Compression, compressedSize: Int): ByteBuffer {
            if(compression == Compression.NONE) return this
            val uncompressedSize = int
            val uncompressed = compression.decompress(array().sliceArray(
                COMP_HEADER_SIZE until COMP_HEADER_SIZE+ compressedSize), uncompressedSize
            )
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