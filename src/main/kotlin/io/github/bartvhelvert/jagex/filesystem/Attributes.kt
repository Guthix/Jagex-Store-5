package io.github.bartvhelvert.jagex.filesystem

import java.nio.ByteBuffer

class IndexAttributes() {
    companion object {
        const val whirlPoolHashByteCount = 64

        const val MASK_NAME_HASH = 0x01
        const val MASK_WHIRLPOOL_HASH = 0x02
        const val MASK_SIZES = 0x04
        const val MASK_UNKNOWN_HASH = 0x08

        @ExperimentalUnsignedTypes
        internal fun decode(buffer: ByteBuffer) {
            val format = buffer.uByte.toInt()
            require(format in 5..7)
            val version = if (format == 5) 0 else buffer.int
            val flags = buffer.uByte.toInt()
            val dictionaryCount = if (format == 7) buffer.smart else buffer.uShort.toInt()
            val dictionaryIds = IntArray(dictionaryCount)
            var biggestId = -1
            var entryId = 0
            for(i in dictionaryIds.indices) {
                val delta = if (format == 7) buffer.smart else buffer.uShort.toInt() // difference with previous id
                entryId += delta
                dictionaryIds[i] = entryId
            }
            val dictionaryNameHashes= if (flags and MASK_NAME_HASH != 0) IntArray(dictionaryCount){
                buffer.int
            } else null
            val crcs = IntArray(dictionaryCount) { buffer.int }
            val unkownHashes = if (flags and MASK_UNKNOWN_HASH != 0) IntArray(dictionaryCount){
                buffer.int
            } else null
            val whirlpoolHashes = if (flags and MASK_WHIRLPOOL_HASH != 0) Array(dictionaryCount) {
                ByteArray(whirlPoolHashByteCount) { buffer.get() }
            } else null
            val dictionarySizes = if(flags and MASK_SIZES != 0) Array(dictionaryCount) {
                ArchiveAttributes.Size(compressed = buffer.int, uncompressed = buffer.int)
            } else null
            val versions = Array(dictionaryCount) { buffer.int }
            //TODO more work
        }
    }
}

class ArchiveAttributes() {
    class Size(compressed: Int, uncompressed: Int)
}

class FileAttributes