package io.github.bartvhelvert.jagex.filesystem

import java.io.File
import io.github.bartvhelvert.jagex.filesystem.store.FileStore
import java.io.IOException

class Cache(directory: File) {
    private val fileStore = FileStore(directory)

    private val indexAttributesCache = mutableMapOf<Int, IndexAttributes>()

    @ExperimentalUnsignedTypes
    fun readDictionary(indexFileId: Int, dictionaryId: Int): Dictionary {
        val indexAttributes = readAndCacheIndexAttributes(indexFileId)
        val dataContainer: Container = Container.decode(fileStore.read(indexFileId, dictionaryId))
        val dictionaryAttributes = indexAttributes.dictionaryAttributes[indexFileId]
            ?: throw IOException("Dictionary attributes to not exist in the cache")
        return Dictionary.decode(dataContainer, dictionaryAttributes)
    }

    @ExperimentalUnsignedTypes
    private fun readAndCacheIndexAttributes(indexFileId: Int): IndexAttributes {
        val indexAttributesContainer = Container.decode(fileStore.read(FileStore.META_DATA_INDEX, indexFileId))
        val indexAttributes = IndexAttributes.decode(indexAttributesContainer)
        indexAttributesCache[indexFileId] = indexAttributes
        return indexAttributes
    }
}