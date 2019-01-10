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
    fun readArchives(dictionaryId: Int, xteas: Map<Int, IntArray> = emptyMap()): Map<Int, Archive> {
        if(dictionaryId !in 0..dictionaryAttributes.size) throw IOException("Dictionary does not exist")
        val dictAttributes = dictionaryAttributes[dictionaryId]
        val archives = mutableMapOf<Int, Archive>()
        for((id, archiveAttr) in dictAttributes.archiveAttributes) {
            val xtea = xteas[id] ?: XTEA.ZERO_KEY
            val archiveContainer = Container.decode(fileStore.read(dictionaryId, id), xtea)
            archives[id] = Archive.decode(archiveContainer, archiveAttr)
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