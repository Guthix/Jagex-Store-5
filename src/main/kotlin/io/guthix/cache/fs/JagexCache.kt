/*
 * Copyright (C) 2019 Guthix
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.fs

import java.io.File
import io.guthix.cache.fs.store.FileStore
import io.guthix.cache.fs.util.*
import mu.KotlinLogging
import java.io.IOException
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

open class JagexCache(directory: File) {
    private val fileStore = FileStore(directory)

    @ExperimentalUnsignedTypes
    protected val dictionaryAttributes = Array(fileStore.dictionaryCount) {
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
    open fun readRawData(indexId: Int, containerId: Int): ByteBuffer = fileStore.read(indexId, containerId)

    @ExperimentalUnsignedTypes
    open fun readArchive(
        dictionaryId: Int,
        archiveId: Int,
        xteaKey: IntArray = XTEA.ZERO_KEY
    ): Archive {
        val dictAttributes = getDictAttributes(dictionaryId)
        val archiveAttributes = dictAttributes.archiveAttributes[archiveId]
            ?: throw IOException("Archive does not exist.")
        val archiveContainer =
            Container.decode(readRawData(dictionaryId, archiveId), xteaKey)
        return Archive.decode(archiveContainer, archiveAttributes)
    }

    @ExperimentalUnsignedTypes
    open fun readArchive(
        dictionaryId: Int,
        archiveName: String,
        xteaKey: IntArray = XTEA.ZERO_KEY
    ): Archive {
        val dictAttributes = getDictAttributes(dictionaryId)
        val nameHash = djb2Hash(archiveName)
        val archiveAttributes =  dictAttributes.archiveAttributes.values.first { it.nameHash == nameHash}
        val archiveContainer =
            Container.decode(readRawData(dictionaryId, archiveAttributes.id), xteaKey)
        return Archive.decode(archiveContainer, archiveAttributes)
    }


    @ExperimentalUnsignedTypes
    open fun readArchives(
        dictionaryId: Int,
        xteaKeys: Map<Int, IntArray> = emptyMap()
    ): Map<Int, Archive> {
        val dictAttributes = getDictAttributes(dictionaryId)
        val archives = mutableMapOf<Int, Archive>()
        dictAttributes.archiveAttributes.forEach { archiveId, archiveAttributes ->
            val xtea = xteaKeys[archiveId] ?: XTEA.ZERO_KEY
            val archiveContainer =
                Container.decode(readRawData(dictionaryId, archiveId), xtea)
            archives[archiveId] = Archive.decode(archiveContainer, archiveAttributes)
        }
        return archives
    }

    @ExperimentalUnsignedTypes
    fun writeArchive(
        dictionaryId: Int,
        archiveId: Int,
        fileBuffers: Map<Int, ByteBuffer>,
        archiveGroupCount: Int = 1,
        archiveVersion: Int = -1,
        attributesVersion: Int? = null,
        archiveContainerVersion: Int = -1,
        attributesContainerVersion: Int = -1,
        archiveXteaKey: IntArray = XTEA.ZERO_KEY,
        attributesXteaKey: IntArray = XTEA.ZERO_KEY,
        archiveCompression: Compression = Compression.GZIP,
        attributesCompression: Compression = Compression.GZIP
    ) {
        val archiveBuffer = ByteBuffer.allocate(fileBuffers.values.sumBy { it.limit() })
        fileBuffers.values.forEach { archiveBuffer.put(it) }
        val files = mutableMapOf<Int, Archive.File>()
        fileBuffers.forEach { id, data ->  files[id] = Archive.File(id, data, null)}
        val archive = Archive(
            archiveId,
            null,
            calculateCRC(archiveBuffer),
            null,
            whirlPoolHash(archiveBuffer.array()),
            ArchiveAttributes.Size(null, archiveBuffer.limit()),
            archiveVersion,
            files
        )
        writeArchive(
            dictionaryId,
            archive,
            archiveGroupCount,
            attributesVersion,
            archiveContainerVersion,
            attributesContainerVersion,
            archiveXteaKey,
            attributesXteaKey,
            archiveCompression,
            attributesCompression
        )
    }

    @ExperimentalUnsignedTypes
    fun writeArchive(
        dictionaryId: Int,
        archive: Archive,
        archiveGroupCount: Int = 1,
        attributesVersion: Int? = null,
        archiveContainerVersion: Int = -1,
        attributesContainerVersion: Int = -1,
        archiveXteaKey: IntArray = XTEA.ZERO_KEY,
        attributesXteaKey: IntArray = XTEA.ZERO_KEY,
        archiveCompression: Compression = Compression.GZIP,
        attributesCompression: Compression = Compression.GZIP
    ) {
        if(dictionaryId > dictionaryAttributes.size) throw IOException(
            "Can not create dictionary with id $dictionaryId, expected: ${dictionaryAttributes.size}"
        )
        val compressedSize = writeArchiveData(
            dictionaryId, archive, archiveGroupCount, archiveContainerVersion, archiveXteaKey, archiveCompression
        )
        writeArchiveAttributes(
            dictionaryId,
            archive,
            compressedSize,
            attributesVersion,
            attributesContainerVersion,
            attributesXteaKey,
            attributesCompression
        )
    }

    @ExperimentalUnsignedTypes
    private fun writeArchiveData(
        dictionaryId: Int,
        archive: Archive,
        archiveGroupCount: Int = 1,
        containerVersion: Int = -1,
        xteaKey: IntArray = XTEA.ZERO_KEY,
        compression: Compression
    ): Int {
        val container = archive.encode(archiveGroupCount, containerVersion)
        val data = container.encode(compression, xteaKey)
        fileStore.write(dictionaryId, archive.id, data)
        return data.limit()
    }

    @ExperimentalUnsignedTypes
    private fun writeArchiveAttributes(
        dictionaryId: Int,
        archive: Archive,
        compressedSize: Int,
        attributesVersion: Int? = null,
        containerVersion: Int = -1,
        xteaKey: IntArray = XTEA.ZERO_KEY,
        compression: Compression = Compression.GZIP
    ) {
        val dictAtrributes = if(dictionaryId == dictionaryAttributes.size) {
            DictionaryAttributes(0, mutableMapOf())
        } else {
            getDictAttributes(dictionaryId)
        }
        if(attributesVersion == null) dictAtrributes.version++ else dictAtrributes.version = attributesVersion
        val fileAttr = mutableMapOf<Int, FileAttributes>()
        archive.files.forEach { id, file ->
            fileAttr[id] = FileAttributes(id, file.nameHash)
        }
        dictAtrributes.archiveAttributes[archive.id] = ArchiveAttributes(
            archive.id,
            null,
            archive.crc,
            archive.unknownHash,
            archive.whirlpoolHash,
            ArchiveAttributes.Size(compressedSize, archive.sizes?.uncompressed),
            archive.version,
            fileAttr
        )
        fileStore.write(
            FileStore.ATTRIBUTE_INDEX,
            dictionaryId,
            dictAtrributes.encode(containerVersion).encode(compression, xteaKey)
        )
    }

    @ExperimentalUnsignedTypes
    fun generateChecksum(): CacheChecksum = CacheChecksum(
        Array(dictionaryAttributes.size) { dictionaryId ->
            val dictionaryAttributes = dictionaryAttributes[dictionaryId]
            val rawBuffer = fileStore.read(FileStore.ATTRIBUTE_INDEX, dictionaryId)
            DictionaryChecksum(
                calculateCRC(rawBuffer),
                dictionaryAttributes.version,
                dictionaryAttributes.archiveAttributes.size,
                dictionaryAttributes.archiveAttributes.values
                    .sumBy { if(it.sizes?.compressed != null) it.sizes.uncompressed!! else 0 },
                whirlPoolHash(rawBuffer.array())
            )
        }
    )

    @ExperimentalUnsignedTypes
    private fun getDictAttributes(dictionaryId: Int): DictionaryAttributes {
        if(dictionaryId !in 0..dictionaryAttributes.size) throw IOException("Dictionary does not exist.")
        return dictionaryAttributes[dictionaryId]
    }
}