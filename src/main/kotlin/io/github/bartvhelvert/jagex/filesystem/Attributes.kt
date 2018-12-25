package io.github.bartvhelvert.jagex.filesystem

import io.github.bartvhelvert.jagex.filesystem.io.smart
import io.github.bartvhelvert.jagex.filesystem.io.uByte
import io.github.bartvhelvert.jagex.filesystem.io.uShort
import io.github.bartvhelvert.jagex.filesystem.io.writeSmart
import io.github.bartvhelvert.jagex.filesystem.transform.whirlPoolHashByteCount
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

data class IndexAttributes(val version: Int, val dictionaryAttributes: MutableMap<Int, DictionaryAttributes>) {
    internal fun encode(format: Int): ByteBuffer {
        val byteStr = ByteArrayOutputStream()
        DataOutputStream(byteStr).use {
            it.writeByte(format)
            if(format != 5) it.writeInt(version)
            var flags = 0
            val hasNameHashes = dictionaryAttributes.values.any {
                it.nameHash != null || it.fileAttributes.values.any { it.nameHash != null }
            }
            val hasUnknownHashes = dictionaryAttributes.values.any { it.unknownHash != null }
            val hasWhirlPoolHashes = dictionaryAttributes.values.any { it.whirlpoolHash != null }
            val hasSizes = dictionaryAttributes.values.any { it.sizes != null }
            if(hasNameHashes) flags = flags or MASK_NAME_HASH
            if(hasUnknownHashes) flags = flags or MASK_UNKNOWN_HASH
            if(hasWhirlPoolHashes) flags = flags or MASK_WHIRLPOOL_HASH
            if(hasSizes) flags = flags or MASK_SIZES
            it.writeByte(flags)
            if(format == 7) it.writeSmart(dictionaryAttributes.size) else it.writeShort(dictionaryAttributes.size)
            var attriDelta = 0
            for(id in dictionaryAttributes.keys) {
                attriDelta += id
                if(format == 7) it.writeSmart(attriDelta) else it.writeShort(attriDelta)
            }
            if(hasNameHashes) {
                for(attr in dictionaryAttributes.values) it.writeInt(attr.nameHash ?: 0)
            }
            for(attr in dictionaryAttributes.values) it.writeInt(attr.crc)
            if(hasUnknownHashes) {
                for(attr in dictionaryAttributes.values) it.writeInt(attr.unknownHash ?: 0)
            }
            if(hasWhirlPoolHashes) {
                for(attr in dictionaryAttributes.values) {
                    it.write(attr.whirlpoolHash ?: ByteArray(whirlPoolHashByteCount))
                }
            }
            if(hasSizes) {
                for(attr in dictionaryAttributes.values) {
                    it.writeInt(attr.sizes?.compressed ?: 0)
                    it.writeInt(attr.sizes?.uncompressed ?: 0)
                }
            }
            for(attr in dictionaryAttributes.values) {
                it.writeInt(version)
            }
            for(attr in dictionaryAttributes.values) {
                if(format == 7) it.writeSmart(attr.fileAttributes.size) else it.writeShort(attr.fileAttributes.size)
            }
            for(attr in dictionaryAttributes.values) {
                var fileDelta = 0
                for(file in attr.fileAttributes.values) {
                    fileDelta += file.id
                    if(format == 7) it.writeSmart(fileDelta) else it.writeShort(fileDelta)
                }
            }
            if(hasNameHashes) {
                for(attr in dictionaryAttributes.values) {
                    for(file in attr.fileAttributes.values) {
                        it.writeInt(file.nameHash ?: 0)
                    }
                }
            }
        }
        return ByteBuffer.wrap(byteStr.toByteArray())
    }

    companion object {
        private const val MASK_NAME_HASH = 0x01
        private const val MASK_WHIRLPOOL_HASH = 0x02
        private const val MASK_SIZES = 0x04
        private const val MASK_UNKNOWN_HASH = 0x08

        @ExperimentalUnsignedTypes
        internal fun decode(container: Container): IndexAttributes {
            val buffer = container.data
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
            val dictNameHashes = if (flags and MASK_NAME_HASH != 0) IntArray(dictCount) {
                buffer.int
            } else null
            val dictCRCs = IntArray(dictCount) { buffer.int }
            val dictUnkownHashes = if (flags and MASK_UNKNOWN_HASH != 0) IntArray(dictCount) {
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

            val dictionaryAttributes = mutableMapOf<Int, DictionaryAttributes>()
            for(dictIndex in dictIds.indices) {
                val fileAttributes = mutableMapOf<Int, FileAttributes>()
                for(fileIndex in dictFileIds[dictIndex].indices) {
                    fileAttributes[dictFileIds[dictIndex][fileIndex]] = FileAttributes(
                        dictFileIds[dictIndex][fileIndex],
                        dictFileNameHashes?.get(dictIndex)?.get(fileIndex)
                    )
                }
                dictionaryAttributes[dictIds[dictIndex]] = DictionaryAttributes(
                    dictIds[dictIndex],
                    dictNameHashes?.get(dictIndex),
                    dictCRCs[dictIndex],
                    dictUnkownHashes?.get(dictIndex),
                    dictWhirlpoolHashes?.get(dictIndex),
                    dictSizes?.get(dictIndex),
                    dictVersions[dictIndex],
                    fileAttributes
                )
            }
            return IndexAttributes(version, dictionaryAttributes)
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
    val fileAttributes: MutableMap<Int, FileAttributes>

) {
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
        if (fileAttributes != other.fileAttributes) return false

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
        result = 31 * result + fileAttributes.hashCode()
        return result
    }

    class Size(val compressed: Int, val uncompressed: Int)
}

data class FileAttributes(val id: Int, val nameHash: Int?)