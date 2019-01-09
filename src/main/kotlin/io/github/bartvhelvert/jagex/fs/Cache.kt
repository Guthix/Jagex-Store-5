package io.github.bartvhelvert.jagex.fs

import java.io.File
import io.github.bartvhelvert.jagex.fs.store.FileStore
import io.github.bartvhelvert.jagex.fs.transform.XTEA
import java.io.IOException

class Cache(directory: File) {
    private val fileStore = FileStore(directory)

    @ExperimentalUnsignedTypes
    private val dictionaryAttributesCache = Array(fileStore.indexFileCount) {
        DictionaryAttributes.decode(
            Container.decode(fileStore.read(FileStore.ATTRIBUTE_INDEX, it))
        )
    }

    private val archiveCache = mutableMapOf<Int, MutableMap<Int, Archive>>()

    @ExperimentalUnsignedTypes
    fun readArchive(
        dictionaryId: Int,
        archiveId: Int,
        xteaKey: IntArray = XTEA.ZERO_KEY,
        shouldCache: Boolean = false
    ): Archive {
        if(dictionaryId !in 0..dictionaryAttributesCache.size) throw IOException("Dictionary does not exist")
        val dictionaryAttributes = dictionaryAttributesCache[dictionaryId]
        val archiveAttributes = dictionaryAttributes.archiveAttributes[archiveId]
            ?: throw IOException("Archive does not exist")
        val archiveContainer = Container.decode(fileStore.read(dictionaryId, archiveId), xteaKey)
        val archive = Archive.decode(archiveContainer, archiveAttributes)
        if(shouldCache) archiveCache.computeIfAbsent(dictionaryId) { mutableMapOf() }[archiveId] = archive
        return archive
    }
}