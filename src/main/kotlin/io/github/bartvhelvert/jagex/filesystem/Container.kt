package io.github.bartvhelvert.jagex.filesystem

import io.github.bartvhelvert.jagex.filesystem.io.uByte
import io.github.bartvhelvert.jagex.filesystem.transform.Compression
import io.github.bartvhelvert.jagex.filesystem.transform.XTEA
import io.github.bartvhelvert.jagex.filesystem.transform.xteaDecrypt
import io.github.bartvhelvert.jagex.filesystem.transform.xteaEncrypt
import java.io.IOException
import java.nio.ByteBuffer

internal data class Container(var version: Int = -1, val data: ByteBuffer) {
    internal fun encode(compression: Compression, xteaKey: IntArray = XTEA.ZERO_KEY): ByteBuffer {
        require(xteaKey.size == XTEA.KEY_SIZE)
        val compressedData = compression.compress(data.array())
        val buffer = ByteBuffer.allocate(ENC_HEADER_SIZE + compression.headerSize + compressedData.size)
        buffer.put(compression.opcode)
        buffer.putInt(compressedData.size)
        if(compression != Compression.NONE) buffer.putInt(data.limit())
        buffer.put(compressedData)
        if(isVersioned()) buffer.putShort(version.toShort())
        return if(xteaKey.all { it != 0 }) {
            buffer.xteaEncrypt(
                key = xteaKey,
                start = ENC_HEADER_SIZE,
                end = ENC_HEADER_SIZE + compression.headerSize + compressedData.size
            )
        } else buffer
    }

    fun isVersioned() = version != -1

    fun removeVersion() {
        version = -1
    }

    companion object {
        private const val ENC_HEADER_SIZE = 5

        @ExperimentalUnsignedTypes
        internal fun decode(buffer: ByteBuffer, xteaKey: IntArray = XTEA.ZERO_KEY): Container {
            require(xteaKey.size == XTEA.KEY_SIZE)
            val compression = Compression.getByOpcode(buffer.uByte.toInt())
            val compressedSize = buffer.int
            if(xteaKey.all { it != 0 }) {
                buffer.xteaDecrypt(
                    key = xteaKey,
                    start = ENC_HEADER_SIZE,
                    end = ENC_HEADER_SIZE + compression.headerSize + compressedSize
                )
            }
            val dataBuffer = if(compression != Compression.NONE) {
                val uncompressedSize = buffer.int
                val headerLength = ENC_HEADER_SIZE + compression.headerSize
                val uncompressed = compression.decompress(buffer.array().sliceArray(
                    headerLength until headerLength + compressedSize), uncompressedSize
                )
                if (uncompressed.size != uncompressedSize) throw IOException("Compression size mismatch")
                ByteBuffer.wrap(uncompressed)
            } else buffer
            buffer.position(ENC_HEADER_SIZE + compression.headerSize + compressedSize)
            val version = if(buffer.remaining() >= 2) buffer.short.toInt() else -1
            return Container(version, dataBuffer)
        }
    }
}