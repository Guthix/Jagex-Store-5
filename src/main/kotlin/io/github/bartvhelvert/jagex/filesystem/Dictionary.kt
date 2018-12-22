package io.github.bartvhelvert.jagex.filesystem

import java.nio.ByteBuffer

data class Dictionary(val attributes: DictionaryAttributes, val entries: Array<ByteBuffer>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Dictionary
        if (attributes != other.attributes) return false
        if (!entries.contentEquals(other.entries)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = attributes.hashCode()
        result = 31 * result + entries.contentHashCode()
        return result
    }

    companion object {
        @ExperimentalUnsignedTypes
        internal fun decode(container: Container, attributes: DictionaryAttributes): Dictionary {
            require(container.version == attributes.version)
            val buffer = container.data
            val fileCount = attributes.fileAttributes.size
            val fileSizes = IntArray(fileCount)
            val chunkCount = buffer.getUByte(buffer.limit() - 1).toInt()
            val chunkSizes = Array(chunkCount) { IntArray(fileCount) }
            buffer.position(buffer.limit() - 1 - chunkCount * fileCount * 4)
            for (chunkId in 0 until chunkCount) {
                var chunkSize = 0
                for (fileId in 0 until fileCount) {
                    val delta = buffer.int // difference in chunk size compared to the previous chunk
                    chunkSize += delta
                    chunkSizes[chunkId][fileId] = chunkSize
                    fileSizes[fileId] += chunkSize
                }
            }
            val fileData = Array<ByteBuffer>(fileCount) {
                ByteBuffer.allocate(fileSizes[it])
            }
            buffer.position(0)
            for (chunkId in 0 until chunkCount) {
                for (fileId in 0 until fileCount) {
                    val chunkSize = chunkSizes[chunkId][fileId]
                    val temp = ByteArray(chunkSize)
                    buffer.get(temp)
                    fileData[fileId].put(temp)
                }
            }
            return Dictionary(attributes, fileData)
        }
    }
}