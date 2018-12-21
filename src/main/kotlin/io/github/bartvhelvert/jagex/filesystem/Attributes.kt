package io.github.bartvhelvert.jagex.filesystem

import java.nio.ByteBuffer

data class IndexAttributes(val version: Int, val dictionaryAttributes: Array<DictionaryAttributes>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IndexAttributes

        if (version != other.version) return false
        if (!dictionaryAttributes.contentEquals(other.dictionaryAttributes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + dictionaryAttributes.contentHashCode()
        return result
    }

    companion object {
        const val whirlPoolHashByteCount = 64

        const val MASK_NAME_HASH = 0x01
        const val MASK_WHIRLPOOL_HASH = 0x02
        const val MASK_SIZES = 0x04
        const val MASK_UNKNOWN_HASH = 0x08

        @ExperimentalUnsignedTypes
        internal fun decode(buffer: ByteBuffer): IndexAttributes {
            val format = buffer.uByte.toInt()
            require(format in 5..7)
            val version = if (format == 5) 0 else buffer.int
            val flags = buffer.uByte.toInt()
            val dictCount = if (format == 7) buffer.smart else buffer.uShort.toInt()
            val dictIds = IntArray(dictCount)
            var dictIdAccumulator = 0
            for(dictIndex in dictIds.indices) {
                val delta = if (format == 7) buffer.smart else buffer.uShort.toInt() // difference with previous id
                dictIdAccumulator += delta
                dictIds[dictIndex] = dictIdAccumulator
            }
            val dictNameHashes= if (flags and MASK_NAME_HASH != 0) IntArray(dictCount){
                buffer.int
            } else null
            val dictCRCs = IntArray(dictCount) { buffer.int }
            val dictUnkownHashes = if (flags and MASK_UNKNOWN_HASH != 0) IntArray(dictCount){
                buffer.int
            } else null
            val dictWhirlpoolHashes = if (flags and MASK_WHIRLPOOL_HASH != 0) Array(dictCount) {
                ByteArray(whirlPoolHashByteCount) { buffer.get() }
            } else null
            val dictSizes = if(flags and MASK_SIZES != 0) Array(dictCount) {
                DictionaryAttributes.Size(compressed = buffer.int, uncompressed = buffer.int)
            } else null
            val dictVersions = Array(dictCount) { buffer.int }
            val dictFileIds = Array(dictCount) {
                IntArray(if (format == 7) buffer.smart else buffer.uShort.toInt()) // decode file count
            }
            for(dictionary in dictFileIds) {
                var fileIdAccumulator = 0
                for(fileIndex in dictionary.indices) {
                    val delta = if (format == 7) buffer.smart else buffer.uShort.toInt() // difference with previous id
                    fileIdAccumulator += delta
                    dictionary[fileIndex] = fileIdAccumulator
                }
            }
            val dictFileNameHashes = if (flags and MASK_NAME_HASH != 0) {
                Array(dictCount) {
                    IntArray(dictFileIds[it].size) {
                        buffer.int
                    }
                }
            } else null
            return IndexAttributes(
                version,
                Array(dictCount) { dictIndex ->
                    DictionaryAttributes(
                        dictIds[dictIndex],
                        dictNameHashes?.get(dictIndex),
                        dictCRCs[dictIndex],
                        dictUnkownHashes?.get(dictIndex),
                        dictWhirlpoolHashes?.get(dictIndex),
                        dictSizes?.get(dictIndex),
                        dictVersions[dictIndex],
                        Array(dictFileIds[dictIndex].size) { fileIndex ->
                            FileAttributes(
                                dictFileIds[dictIndex][fileIndex],
                                dictFileNameHashes?.get(dictIndex)?.get(fileIndex)
                            )
                        }
                    )
                }
            )
        }
    }
}

data class DictionaryAttributes(
    val id: Int,
    val nameHash: Int?,
    val crc: Int,
    val unknownHash: Int?,
    val whirlpoolHash: ByteArray?,
    val sizes: Size?,
    val version: Int,
    val fileAttributes: Array<FileAttributes>

) {
    class Size(compressed: Int, uncompressed: Int)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DictionaryAttributes

        if (id != other.id) return false
        if (nameHash != other.nameHash) return false
        if (crc != other.crc) return false
        if (unknownHash != other.unknownHash) return false
        if (whirlpoolHash != null) {
            if (other.whirlpoolHash == null) return false
            if (!whirlpoolHash.contentEquals(other.whirlpoolHash)) return false
        } else if (other.whirlpoolHash != null) return false
        if (sizes != other.sizes) return false
        if (version != other.version) return false
        if (!fileAttributes.contentEquals(other.fileAttributes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + (nameHash ?: 0)
        result = 31 * result + crc
        result = 31 * result + (unknownHash ?: 0)
        result = 31 * result + (whirlpoolHash?.contentHashCode() ?: 0)
        result = 31 * result + (sizes?.hashCode() ?: 0)
        result = 31 * result + version
        result = 31 * result + fileAttributes.contentHashCode()
        return result
    }
}

data class FileAttributes(val id: Int, val nameHash: Int?)