package io.github.bartvhelvert.jagex.filesystem.store

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class FileStore(directory: File) {
    private val dataChannel: DataChannel

    private val indexChannels = mutableMapOf<Int, IndexChannel>()

    private val attributeIndexChannel: IndexChannel

    val indexFileCount get() = indexChannels.size

    init {
        if(!directory.isDirectory) throw IOException("$directory is not a directory or doesn't exist")
        val dataFile = directory.resolve("$FILE_NAME.$DATA_FILE_EXTENSION")
        if(!dataFile.isFile) throw IOException("$dataFile is not a file or doesn't exist")
        dataChannel = DataChannel(RandomAccessFile(dataFile, accessMode).channel)
        for (indexFileId in 0 until ATTRIBUTE_INDEX) {
            val indexFile = directory.resolve("$FILE_NAME.$INDEX_FILE_EXTENSION$indexFileId")
            if(!indexFile.isFile) continue
            indexChannels[indexFileId] = IndexChannel(RandomAccessFile(dataFile, accessMode).channel)
        }
        val attributeFile = directory.resolve("$FILE_NAME.$INDEX_FILE_EXTENSION$ATTRIBUTE_INDEX")
        if(!attributeFile.isFile) throw IOException("$attributeFile is not a file or doesn't exist")
        attributeIndexChannel = IndexChannel(RandomAccessFile(attributeFile, accessMode).channel)
    }

    @ExperimentalUnsignedTypes
    fun read(indexFileId: Int, containerId: Int): ByteBuffer {
        val index = if(indexFileId == ATTRIBUTE_INDEX) {
            attributeIndexChannel.read(containerId)
        } else {
            indexChannels[indexFileId]?.read(containerId)
        }
        require(index != null)
        return dataChannel.read(indexFileId, index, containerId)
    }

    companion object {
        private const val accessMode = "rw"
        private const val DATA_FILE_EXTENSION = "dat2"
        private const val INDEX_FILE_EXTENSION = "idx"
        private const val FILE_NAME = "main_file_cache"
        const val ATTRIBUTE_INDEX = 255
    }
}