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

import io.guthix.cache.fs.util.Compression
import io.guthix.cache.fs.util.XTEA
import io.guthix.cache.fs.util.calculateCRC
import io.guthix.cache.fs.util.whirlPoolHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JagexCacheTest {
    @Test
    @ExperimentalUnsignedTypes
    fun readWriteArchiveSequentialTest(@TempDir cacheDir: File) {
        val filesToWrite = mapOf<Int, Archive.File>(
            1 to Archive.File(ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} }, null),
            2 to Archive.File(ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} }, null),
            3 to Archive.File(ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} }, null)
        )
        readWriteTest(cacheDir, filesToWrite)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun readWriteArchiveNonSequentialFilesTest(@TempDir cacheDir: File) {
        val filesToWrite = mapOf<Int, Archive.File>(
            1 to Archive.File(ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} }, null),
            6 to Archive.File(ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} }, null),
            10 to Archive.File(ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} }, null)
        )
        readWriteTest(cacheDir, filesToWrite)
    }

    @ParameterizedTest
    @MethodSource("fileBuffers")
    @ExperimentalUnsignedTypes
    private fun readWriteTest(
        cacheDir: File,
        filesToWrite: Map<Int, Archive.File>,
        dictionaryId: Int = 0,
        archiveId: Int = 0,
        nameHash: Int? = null,
        archiveGroupCount: Int = 1,
        archiveVersion: Int = -1,
        attributesVersion: Int? = null,
        archiveContainerVersion: Int = -1,
        attributesContainerVersion: Int = -1,
        archiveXteaKey: IntArray = XTEA.ZERO_KEY,
        attributesXteaKey: IntArray = XTEA.ZERO_KEY,
        archiveCompression: Compression = Compression.NONE,
        attributesCompression: Compression = Compression.NONE
    ) {
        JagexCache(cacheDir).use { cache ->
            val archiveBuffer = ByteBuffer.allocate(filesToWrite.values.sumBy { it.data.limit() })
            filesToWrite.values.map{it.data}.forEach { archiveBuffer.put(it) }
            val archive = Archive(
                archiveId,
                nameHash,
                calculateCRC(archiveBuffer),
                null,
                whirlPoolHash(archiveBuffer.array()),
                ArchiveAttributes.Size(null, archiveBuffer.limit()),
                archiveVersion,
                filesToWrite
            )
            cache.writeArchive(dictionaryId, archive, archiveGroupCount, attributesVersion, archiveContainerVersion,
                attributesContainerVersion, archiveXteaKey, attributesXteaKey, archiveCompression, attributesCompression
            )
            val readArchive = cache.readArchive(dictionaryId, archiveId)
            archive.files.values.forEach { it.data.flip() }
            assertEquals(archive, readArchive)
        }
    }
}