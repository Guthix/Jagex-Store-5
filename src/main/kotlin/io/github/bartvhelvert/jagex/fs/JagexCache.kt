package io.github.bartvhelvert.jagex.fs

import java.io.File
import io.github.bartvhelvert.jagex.fs.store.FileStore
import io.github.bartvhelvert.jagex.fs.util.XTEA
import java.io.IOException

class JagexCache(directory: File) {
    private val fileStore = FileStore(directory)

    @ExperimentalUnsignedTypes
    private val dictionaryAttributes = Array(fileStore.dictionaryCount) {
        DictionaryAttributes.decode(
            Container.decode(fileStore.read(FileStore.ATTRIBUTE_INDEX, it))
        )
    }

    @ExperimentalUnsignedTypes
    fun archiveIds(dictionaryId: Int) = dictionaryAttributes[dictionaryId].archiveAttributes.keys

    @ExperimentalUnsignedTypes
    fun fileIds(dictionaryId: Int, archiveId: Int) =
        dictionaryAttributes[dictionaryId].archiveAttributes[archiveId]?.fileAttributes?.keys

    @ExperimentalUnsignedTypes
    fun readArchives(
        dictionaryId: Int,
        xteaKeys: Map<Int, IntArray> = emptyMap()
    ): Map<Int, Archive> {
        if(dictionaryId !in 0..dictionaryAttributes.size) throw IOException("Dictionary does not exist")
        val dictAttributes = dictionaryAttributes[dictionaryId]
        val archives = mutableMapOf<Int, Archive>()
        dictAttributes.archiveAttributes.forEach { archiveId, archiveAttributes ->
            val xtea = xteaKeys[archiveId] ?: XTEA.ZERO_KEY
            val archiveContainer = Container.decode(fileStore.read(dictionaryId, archiveId), xtea)
            archives[archiveId] = Archive.decode(archiveContainer, archiveAttributes)
        }
        return archives
    }

    @ExperimentalUnsignedTypes
    fun readArchive(
        dictionaryId: Int,
        archiveId: Int,
        xteaKey: IntArray = XTEA.ZERO_KEY
    ): Archive {
        if(dictionaryId !in 0..dictionaryAttributes.size) throw IOException("Dictionary does not exist")
        val dictAttributes = dictionaryAttributes[dictionaryId]
        val archiveAttributes = dictAttributes.archiveAttributes[archiveId]
            ?: throw IOException("Archive does not exist")
        val archiveContainer = Container.decode(fileStore.read(dictionaryId, archiveId), xteaKey)
        return Archive.decode(archiveContainer, archiveAttributes)
    }
}