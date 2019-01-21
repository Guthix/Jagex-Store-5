package io.github.bartvhelvert.jagex.fs

import io.github.bartvhelvert.jagex.fs.io.uByte
import io.github.bartvhelvert.jagex.fs.util.Compression
import io.github.bartvhelvert.jagex.fs.util.XTEA
import io.github.bartvhelvert.jagex.fs.util.xteaDecrypt
import io.github.bartvhelvert.jagex.fs.util.xteaEncrypt
import java.io.IOException
import java.nio.ByteBuffer

data class Container(var version: Int = -1, val data: ByteBuffer) {
    internal fun encode(compression: Compression, xteaKey: IntArray = XTEA.ZERO_KEY): ByteBuffer {
        require(xteaKey.size == XTEA.KEY_SIZE)
        val compressedData = compression.compress(data.array())
        val buffer = ByteBuffer.allocate(
            ENC_HEADER_SIZE + compression.headerSize + compressedData.size + if(isVersioned) 2 else 0
        )
        buffer.put(compression.opcode)
        buffer.putInt(compressedData.size)
        if(compression != Compression.NONE) buffer.putInt(data.limit())
        buffer.put(compressedData)
        if(isVersioned) buffer.putShort(version.toShort())
        return if(xteaKey.all { it != 0 }) {
            buffer.xteaEncrypt(
                key = xteaKey,
                start = ENC_HEADER_SIZE,
                end = ENC_HEADER_SIZE + compression.headerSize + compressedData.size
            ).flip()
        } else buffer.flip()
    }

    val isVersioned get() = version != -1

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
            } else ByteBuffer.wrap(buffer.array().sliceArray(
                ENC_HEADER_SIZE until ENC_HEADER_SIZE + compressedSize
            ))
            buffer.position(ENC_HEADER_SIZE + compression.headerSize + compressedSize)
            val version = if(buffer.remaining() >= 2) buffer.short.toInt() else -1
            return Container(version, dataBuffer)
        }
    }
}