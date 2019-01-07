package io.github.bartvhelvert.jagex.filesystem

import io.github.bartvhelvert.jagex.filesystem.io.getUByte
import io.github.bartvhelvert.jagex.filesystem.io.splitOf
import java.nio.ByteBuffer

data class Archive(val attributes: ArchiveAttributes, val fileData: Array<ByteBuffer>) {
    internal fun encode(groupCount: Int = 1, containerVersion: Int = -1): Container {
        val buffer = ByteBuffer.allocate(
            fileData.sumBy { it.limit() } + groupCount * fileData.size * Int.SIZE_BYTES + 1
        )
        val groups = divideIntoGroups(groupCount)
        for(group in groups) {
            for(fileGroup in group) {
                buffer.put(fileGroup)
            }
        }
        for(group in groups) {
            var delta = group[0].limit()
            buffer.putInt(delta)
            for(i in 1 until group.size) {
                buffer.putInt(delta - group[i].limit())
                delta = group[i].limit()
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
        other as Archive
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
        internal fun decode(container: Container, attributes: ArchiveAttributes): Archive {
            val buffer = container.data
            val fileCount = attributes.fileAttributes.size
            val fileSizes = IntArray(fileCount)
            val groupCount = buffer.getUByte(buffer.limit() - 1).toInt()
            val groupFileSizes = Array(groupCount) { IntArray(fileCount) }
            buffer.position(buffer.limit() - 1 - groupCount * fileCount * 4)
            for (groupId in 0 until groupCount) {
                var groupFileSize = 0
                for (fileId in 0 until fileCount) {
                    val delta = buffer.int // difference in chunk size compared to the previous chunk
                    groupFileSize += delta
                    groupFileSizes[groupId][fileId] = groupFileSize
                    fileSizes[fileId] += groupFileSize
                }
            }
            val fileData = Array<ByteBuffer>(fileCount) {
                ByteBuffer.allocate(fileSizes[it])
            }
            buffer.position(0)
            for (groupId in 0 until groupCount) {
                for (fileId in 0 until fileCount) {
                    val groupFileSize = groupFileSizes[groupId][fileId]
                    val temp = ByteArray(groupFileSize)
                    buffer.get(temp)
                    fileData[fileId].put(temp)
                }
            }
            fileData.forEach { it.flip() }
            return Archive(attributes, fileData)
        }
    }
}