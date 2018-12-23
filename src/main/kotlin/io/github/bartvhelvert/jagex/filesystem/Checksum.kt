package io.github.bartvhelvert.jagex.filesystem

import io.github.bartvhelvert.jagex.filesystem.transform.rsaCrypt
import io.github.bartvhelvert.jagex.filesystem.transform.whirlPoolHash
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer

data class CacheChecksum(val indexFileChecksums: Array<IndexFileCheckSum>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CacheChecksum
        if (!indexFileChecksums.contentEquals(other.indexFileChecksums)) return false
        return true
    }

    override fun hashCode(): Int {
        return indexFileChecksums.contentHashCode()
    }

    companion object {
        @ExperimentalUnsignedTypes
        fun decode(buffer: ByteBuffer, whirlpool: Boolean, mod: BigInteger?, pubKey: BigInteger?): CacheChecksum {
            val indexFileCount = if (whirlpool) buffer.uByte.toInt() else buffer.limit() / 8
            val masterDigest =
                whirlPoolHash(buffer.array().sliceArray(0..indexFileCount * 80 + 1))
            buffer.position(if (whirlpool) 1 else 0)
            val indexFileChecksums = Array(indexFileCount) {
                val crc = buffer.int
                val version = buffer.int
                val fileCount = if (whirlpool) buffer.int else 0
                val size = if (whirlpool) buffer.int else 0
                val whirlPoolDigest = if (whirlpool) {
                    val digest = ByteArray(64)
                    buffer.get(digest)
                    digest
                } else null
                IndexFileCheckSum(crc, version, fileCount, size, whirlPoolDigest)
            }
            if (whirlpool) {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var temp = ByteBuffer.wrap(bytes)
                if (mod != null && pubKey != null) {
                    temp = rsaCrypt(buffer, mod, pubKey)
                }
                if (temp.limit() != 65) throw IOException("Decrypted data size mismatch")
                for (i in 0..63) {
                    if (temp.get(i + 1) != masterDigest[i]) throw IOException("Whirlpool digest mismatch")
                }
            }
            return CacheChecksum(indexFileChecksums)
        }
    }
}

data class IndexFileCheckSum(
    val crc: Int,
    val version: Int,
    val fileCount: Int,
    val size: Int,
    val whirlpoolDigest: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IndexFileCheckSum
        if (crc != other.crc) return false
        if (version != other.version) return false
        if (fileCount != other.fileCount) return false
        if (size != other.size) return false
        if (whirlpoolDigest != null) {
            if (other.whirlpoolDigest == null) return false
            if (!whirlpoolDigest.contentEquals(other.whirlpoolDigest)) return false
        } else if (other.whirlpoolDigest != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = crc
        result = 31 * result + version
        result = 31 * result + fileCount
        result = 31 * result + size
        result = 31 * result + (whirlpoolDigest?.contentHashCode() ?: 0)
        return result
    }
}