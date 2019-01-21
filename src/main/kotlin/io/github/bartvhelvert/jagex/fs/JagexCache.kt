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
    fun readContainers(
        dictionaryId: Int
    ): Map<Int, Container> {
        if(dictionaryId !in 0..dictionaryAttributes.size) throw IOException("Dictionary does not exist")
        val containers = mutableMapOf<Int, Container>()
        dictionaryAttributes[dictionaryId].archiveAttributes.forEach { containerId, _ ->
            containers[containerId] = Container.decode(fileStore.read(dictionaryId, containerId))
        }
        return containers
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