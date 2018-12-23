package io.github.bartvhelvert.jagex.filesystem

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
        return buffer.encrypt(xteaKey, compression, compressedData.size)
    }

    private fun ByteBuffer.encrypt(xteaKeys: IntArray, compression: Compression, compressedSize: Int) =
        if (xteaKeys.all { it == 0 }) {
            this
        } else {
            xteaEncrypt(xteaKeys, start = ENC_HEADER_SIZE, end = ENC_HEADER_SIZE + compression.headerSize + compressedSize)
        }

    fun isVersioned(): Boolean {
        return version != -1
    }

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
            buffer.decrypt(xteaKey, compression, compressedSize)
            val dataBuffer = buffer.decompress(compression, compressedSize)
            buffer.position(ENC_HEADER_SIZE + compression.headerSize + compressedSize)
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
            val headerLength = ENC_HEADER_SIZE + compression.headerSize
            val uncompressed = compression.decompress(array().sliceArray(
                headerLength until headerLength + compressedSize), uncompressedSize
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