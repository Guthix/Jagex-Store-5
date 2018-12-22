package io.github.bartvhelvert.jagex.filesystem.store

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class FileStore(directory: File) {
    private val dataChannel: DataChannel

    private val indexChannels = mutableMapOf<Int, IndexChannel>()

    private val attributeIndexChannel: IndexChannel

    val indexFileCount get() = indexChannels.size

    init {
        require(directory.isDirectory)
        val dataFile = directory.resolve("$FILE_NAME.$DATA_FILE_EXTENSION")
        require(dataFile.isFile)
        dataChannel = DataChannel(RandomAccessFile(dataFile, accessMode).channel)
        for (indexFileId in 0 until META_DATA_INDEX) {
            val indexFile = directory.resolve("$FILE_NAME$indexFileId.$INDEX_FILE_EXTENSION")
            if(!indexFile.isFile) continue
            indexChannels[indexFileId] = IndexChannel(RandomAccessFile(dataFile, accessMode).channel)
        }
        val metaDataFile = directory.resolve("$FILE_NAME$META_DATA_INDEX.$INDEX_FILE_EXTENSION")
        require(metaDataFile.isFile)
        attributeIndexChannel = IndexChannel(RandomAccessFile(dataFile, accessMode).channel)
    }

    @ExperimentalUnsignedTypes
    fun read(indexFileId: Int, containerId: Int): ByteBuffer {
        val index = indexChannels[indexFileId]?.read(containerId)
        require(index != null)
        return dataChannel.read(indexFileId, index, containerId)
    }

    companion object {
        private const val accessMode = "rw"
        private const val DATA_FILE_EXTENSION = "dat2"
        private const val INDEX_FILE_EXTENSION = "idx"
        private const val FILE_NAME = "main_file_cache"
        const val META_DATA_INDEX = 255
    }
}