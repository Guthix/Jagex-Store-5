package io.github.bartvhelvert.jagex.fs

import java.io.File
import io.github.bartvhelvert.jagex.fs.store.FileStore
import io.github.bartvhelvert.jagex.fs.transform.XTEA
import java.io.IOException

class Cache(directory: File) {
    private val fileStore = FileStore(directory)

    @ExperimentalUnsignedTypes
    private val dictionaryAttributesCache = arrayOfNulls<DictionaryAttributes?>(fileStore.indexFileCount)

    private val archiveCache = mutableMapOf<Int, MutableMap<Int, Archive>>()

    @ExperimentalUnsignedTypes
    fun readArchive(
        dictionaryId: Int,
        archiveId: Int,
        xteaKey: IntArray = XTEA.ZERO_KEY,
        shouldCache: Boolean = false
    ): Archive {
        val indexAttributes = readIndexAttributes(dictionaryId)
        val dataContainer: Container = Container.decode(fileStore.read(dictionaryId, archiveId), xteaKey)
        val archiveAttributes = indexAttributes.archiveAttributes[archiveId]
            ?: throw IOException("Archie attributes do not exist")
        val archive = Archive.decode(dataContainer, archiveAttributes)
        if(shouldCache) archiveCache.computeIfAbsent(dictionaryId) { mutableMapOf() }[archiveId] = archive
        return archive
    }

    @ExperimentalUnsignedTypes
    private fun readIndexAttributes(dictionaryId: Int): DictionaryAttributes {
        val cachedAttributes = dictionaryAttributesCache[dictionaryId]
        return if(cachedAttributes != null) {
            cachedAttributes
        } else {
            val readAttributes = DictionaryAttributes.decode(
                Container.decode(fileStore.read(FileStore.ATTRIBUTE_INDEX, dictionaryId))
            )
            dictionaryAttributesCache[dictionaryId] = readAttributes
            readAttributes
        }
    }
}