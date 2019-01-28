/*
Copyright (C) 2019 Guthix

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.fs

import java.io.File
import io.guthix.cache.fs.store.FileStore
import io.guthix.cache.fs.util.XTEA
import io.guthix.cache.fs.util.djb2Hash
import java.io.IOException
import java.nio.ByteBuffer

class JagexCache(directory: File) {
    private val fileStore = FileStore(directory)

    @ExperimentalUnsignedTypes
    private val dictionaryAttributes = Array(fileStore.dictionaryCount) {
        DictionaryAttributes.decode(
            Container.decode(
                fileStore.read(
                    FileStore.ATTRIBUTE_INDEX,
                    it
                )
            )
        )
    }

    @ExperimentalUnsignedTypes
    fun archiveIds(dictionaryId: Int) = getDictAttributes(dictionaryId).archiveAttributes.keys

    @ExperimentalUnsignedTypes
    fun fileIds(dictionaryId: Int, archiveId: Int) =
        getDictAttributes(dictionaryId).archiveAttributes[archiveId]?.fileAttributes?.keys

    @ExperimentalUnsignedTypes
    fun readRawData(indexId: Int, containerId: Int): ByteBuffer = fileStore.read(indexId, containerId)

    @ExperimentalUnsignedTypes
    fun readArchive(
        dictionaryId: Int,
        archiveName: String,
        xteaKey: IntArray = XTEA.ZERO_KEY
    ): Archive {
        val dictAttributes = getDictAttributes(dictionaryId)
        val nameHash = djb2Hash(archiveName)
        val archiveAttributes =  dictAttributes.archiveAttributes.values.first { it.nameHash == nameHash}
        val archiveContainer =
            Container.decode(fileStore.read(dictionaryId, archiveAttributes.id), xteaKey)
        return Archive.decode(archiveContainer, archiveAttributes)
    }

    @ExperimentalUnsignedTypes
    fun readArchive(
        dictionaryId: Int,
        archiveId: Int,
        xteaKey: IntArray = XTEA.ZERO_KEY
    ): Archive {
        val dictAttributes = getDictAttributes(dictionaryId)
        val archiveAttributes = dictAttributes.archiveAttributes[archiveId]
            ?: throw IOException("Archive does not exist")
        val archiveContainer =
            Container.decode(fileStore.read(dictionaryId, archiveId), xteaKey)
        return Archive.decode(archiveContainer, archiveAttributes)
    }

    @ExperimentalUnsignedTypes
    fun readArchives(
        dictionaryId: Int,
        xteaKeys: Map<Int, IntArray> = emptyMap()
    ): Map<Int, Archive> {
        val dictAttributes = getDictAttributes(dictionaryId)
        val archives = mutableMapOf<Int, Archive>()
        dictAttributes.archiveAttributes.forEach { archiveId, archiveAttributes ->
            val xtea = xteaKeys[archiveId] ?: XTEA.ZERO_KEY
            val archiveContainer =
                Container.decode(fileStore.read(dictionaryId, archiveId), xtea)
            archives[archiveId] = Archive.decode(archiveContainer, archiveAttributes)
        }
        return archives
    }

    @ExperimentalUnsignedTypes
    private fun getDictAttributes(dictionaryId: Int): DictionaryAttributes {
        if(dictionaryId !in 0..dictionaryAttributes.size) throw IOException("Dictionary does not exist")
        return dictionaryAttributes[dictionaryId]
    }
}