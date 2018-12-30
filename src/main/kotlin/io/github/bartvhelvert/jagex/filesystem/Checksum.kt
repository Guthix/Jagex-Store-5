package io.github.bartvhelvert.jagex.filesystem

import io.github.bartvhelvert.jagex.filesystem.io.uByte
import io.github.bartvhelvert.jagex.filesystem.transform.rsaCrypt
import io.github.bartvhelvert.jagex.filesystem.transform.whirlPoolHash
import io.github.bartvhelvert.jagex.filesystem.transform.whirlPoolHashByteCount
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer

data class CacheChecksum(val indexFileChecksums: Array<IndexFileChecksum>) {
    fun encode(mod: BigInteger?, pubKey: BigInteger?): ByteBuffer {
        val whirlpool = indexFileChecksums.all { it.whirlpoolDigest != null }
        val buffer = ByteBuffer.allocate(if(whirlpool)
            WP_ENCODED_SIZE + IndexFileChecksum.WP_ENCODED_SIZE * indexFileChecksums.size
        else
            IndexFileChecksum.ENCODED_SIZE * indexFileChecksums.size
        )
        if(whirlpool) buffer.put(indexFileChecksums.size.toByte())
        for(indexFileChecksum in indexFileChecksums) {
            buffer.putInt(indexFileChecksum.crc)
            buffer.putInt(indexFileChecksum.version)
            if(whirlpool) {
                buffer.putInt(indexFileChecksum.fileCount)
                buffer.putInt(indexFileChecksum.size)
                buffer.put(indexFileChecksum.whirlpoolDigest)
            }
        }
        if(whirlpool) {
            val hash = if(mod != null && pubKey != null) {
                rsaCrypt(whirlPoolHash(buffer.array().sliceArray(1 until buffer.position())), mod, pubKey)
            } else {
                whirlPoolHash(buffer.array().sliceArray(1 until buffer.position()))
            }
            buffer.put(hash)
        }
        return buffer.flip()
    }

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
        const val WP_ENCODED_SIZE = whirlPoolHashByteCount + 1

        @ExperimentalUnsignedTypes
        fun decode(buffer: ByteBuffer, whirlpool: Boolean, mod: BigInteger?, privateKey: BigInteger?): CacheChecksum {
            val indexFileCount = if (whirlpool) buffer.uByte.toInt() else buffer.limit() / IndexFileChecksum.ENCODED_SIZE
            val indexFileEncodedSize = if(whirlpool) {
                IndexFileChecksum.WP_ENCODED_SIZE * indexFileCount
            } else {
                IndexFileChecksum.ENCODED_SIZE * indexFileCount
            }
            val indexFileEncodedStart = if (whirlpool) 1 else 0
            val calculatedDigest = whirlPoolHash(
                buffer.array().sliceArray(indexFileEncodedStart until indexFileEncodedStart + indexFileEncodedSize)
            )
            val indexFileChecksums = Array(indexFileCount) {
                val crc = buffer.int
                val version = buffer.int
                val fileCount = if (whirlpool) buffer.int else 0
                val indexFileSize = if (whirlpool) buffer.int else 0
                val whirlPoolDigest = if (whirlpool) {
                    val digest = ByteArray(whirlPoolHashByteCount)
                    buffer.get(digest)
                    digest
                } else null
                IndexFileChecksum(crc, version, fileCount, indexFileSize, whirlPoolDigest)
            }
            if (whirlpool) {
                val hash= if (mod != null && privateKey != null) {
                    rsaCrypt(
                        buffer.array().sliceArray(buffer.position() until buffer.position() + buffer.remaining()),
                        mod,
                        privateKey
                    )
                } else {
                    buffer.array().sliceArray(buffer.position() until buffer.position() + buffer.remaining())
                }
                if (!hash.contentEquals(calculatedDigest)) throw IOException("Whirlpool digest does not match")
            }
            return CacheChecksum(indexFileChecksums)
        }
    }
}

data class IndexFileChecksum(
    val crc: Int,
    val version: Int,
    val fileCount: Int,
    val size: Int,
    val whirlpoolDigest: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IndexFileChecksum
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

    companion object {
        internal const val ENCODED_SIZE = 8
        internal const val WP_ENCODED_SIZE = ENCODED_SIZE + whirlPoolHashByteCount + 8
    }
}