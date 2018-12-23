package io.github.bartvhelvert.jagex.filesystem

import java.io.File
import io.github.bartvhelvert.jagex.filesystem.store.FileStore
import io.github.bartvhelvert.jagex.filesystem.transform.XTEA
import java.io.IOException

class Cache(directory: File) {
    private val fileStore = FileStore(directory)

    private val indexAttributesCache = mutableMapOf<Int, IndexAttributes>()

    private val dictionaryCache = mutableMapOf<Int, MutableMap<Int, Dictionary>>()

    @ExperimentalUnsignedTypes
    fun readDictionary(
        indexFileId: Int,
        dictionaryId: Int,
        xteaKey: IntArray = XTEA.ZERO_KEY,
        shouldCache: Boolean = false
    ): Dictionary {
        val indexAttributes = readIndexAttributes(indexFileId)
        val dataContainer: Container = Container.decode(fileStore.read(indexFileId, dictionaryId), xteaKey)
        val dictionaryAttributes = indexAttributes.dictionaryAttributes[dictionaryId]
            ?: throw IOException("Dictionary attributes to not exist in the shouldCache")
        val dictionary = Dictionary.decode(dataContainer, dictionaryAttributes)
        if(shouldCache) dictionaryCache.computeIfAbsent(indexFileId) { mutableMapOf() }[dictionaryId] = dictionary
        return dictionary
    }

    @ExperimentalUnsignedTypes
    private fun readIndexAttributes(indexFileId: Int) = indexAttributesCache.computeIfAbsent(indexFileId) {
        val indexAttributesContainer = Container.decode(fileStore.read(FileStore.ATTRIBUTE_INDEX, indexFileId))
        IndexAttributes.decode(indexAttributesContainer)
    }
}