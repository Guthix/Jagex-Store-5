package io.github.bartvhelvert.jagex.filesystem

import io.github.bartvhelvert.jagex.filesystem.io.getUByte
import io.github.bartvhelvert.jagex.filesystem.io.splitOf
import java.nio.ByteBuffer

data class Dictionary(val attributes: DictionaryAttributes, val fileData: Array<ByteBuffer>) {
    internal fun encode(groupCount: Int = 1, containerVersion: Int = -1): Container {
        val buffer = ByteBuffer.allocate(fileData.sumBy { it.limit() } + fileData.size * Int.SIZE_BYTES + 1)
        val groups = divideIntoGroups(groupCount)
        for(group in groups) {
            for(fileGroup in group) {
                buffer.put(fileGroup)
            }
        }
        for(group in groups) {
            for(fileGroup in group) {
                buffer.putInt(fileGroup.limit())
            }
        }
        buffer.put(groupCount.toByte())
        return Container(containerVersion, buffer)
    }

    private fun divideIntoGroups(groupCount: Int): Array<Array<ByteBuffer>> = Array(groupCount) { group ->
        Array(fileData.size) { file ->
            fileData[file].splitOf(group + 1, groupCount)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Dictionary
        if (attributes != other.attributes) return false
        if (!fileData.contentEquals(other.fileData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = attributes.hashCode()
        result = 31 * result + fileData.contentHashCode()
        return result
    }

    companion object {
        @ExperimentalUnsignedTypes
        internal fun decode(container: Container, attributes: DictionaryAttributes): Dictionary {
            val buffer = container.data
            val fileCount = attributes.fileAttributes.size
            val fileSizes = IntArray(fileCount)
            val groupCount = buffer.getUByte(buffer.limit() - 1).toInt()
            val groupFileSizes = Array(groupCount) { IntArray(fileCount) }
            buffer.position(buffer.limit() - 1 - groupCount * fileCount * 4)
            for (groupId in 0 until groupCount) {
                var groupSize = 0
                for (fileId in 0 until fileCount) {
                    val delta = buffer.int // difference in chunk size compared to the previous chunk
                    groupSize += delta
                    groupFileSizes[groupId][fileId] = groupSize
                    fileSizes[fileId] += groupSize
                }
            }
            val fileData = Array<ByteBuffer>(fileCount) {
                ByteBuffer.allocate(fileSizes[it])
            }
            buffer.position(0)
            for (groupId in 0 until groupCount) {
                for (fileId in 0 until fileCount) {
                    val groupSize = groupFileSizes[groupId][fileId]
                    val temp = ByteArray(groupSize)
                    buffer.get(temp)
                    fileData[fileId].put(temp)
                }
            }
            return Dictionary(attributes, fileData)
        }
    }
}