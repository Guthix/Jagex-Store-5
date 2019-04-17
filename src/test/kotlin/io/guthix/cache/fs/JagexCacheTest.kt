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

import io.guthix.cache.fs.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JagexCacheTest {
    @Test
    @ExperimentalUnsignedTypes
    fun readWriteArchiveSequentialTest(@TempDir cacheDir: File) {
        readWriteTest(cacheDir, testFiles)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun readWriteArchiveNonSequentialFilesTest(@TempDir cacheDir: File) {
        val nonSeqTestFiles = mapOf(
            1 to Archive.File(ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} }, null),
            6 to Archive.File(ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} }, null),
            10 to Archive.File(ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} }, null)
        )
        readWriteTest(cacheDir, nonSeqTestFiles)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun readWriteNameHashTest(@TempDir cacheDir: File) {
        val seqNameTestFiles = mapOf(
            1 to Archive.File(ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} },
                "SeqTest1".hashCode()
            ),
            2 to Archive.File(ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} },
                "SeqTest2".hashCode()
            ),
            3 to Archive.File(ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} },
                "SeqTest3".hashCode()
            )
        )
        readWriteTest(cacheDir, seqNameTestFiles, nameHash = "Hello World!".hashCode())
    }

    @Test
    @ExperimentalUnsignedTypes
    fun readWriteMultipleGroupsTest(@TempDir cacheDir: File) {
        val testGroups = listOf(1, 3, 8, 10, 20)
        testGroups.forEach { readWriteTest(cacheDir, testFiles, archiveGroupCount = it) }
    }

    @Test
    @ExperimentalUnsignedTypes
    fun readWriteCustomVersionTest(@TempDir cacheDir: File) {
        readWriteTest(cacheDir, testFiles, archiveVersion = 3, attributesVersion = 8)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun readWriteCustomContainerVersionTest(@TempDir cacheDir: File) {
        readWriteTest(cacheDir, testFiles, archiveContainerVersion = 3, attributesContainerVersion = 8)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun readWriteEncryptionTest(@TempDir cacheDir: File) {
        val archiveXtea = intArrayOf(3028, 1, 759, 43945)
        val attributesXtea = intArrayOf(895, 3458790, 4358976, 32470)
        readWriteTest(cacheDir, testFiles, archiveXteaKey = archiveXtea, attributesXteaKey = attributesXtea)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun readWriteCompressionTest(@TempDir cacheDir: File) {
        Compression.values().forEach { archiveCompression ->
            Compression.values().forEach { attributesCompression ->
                readWriteTest(
                    cacheDir,
                    testFiles,
                    archiveCompression = archiveCompression,
                    attributesCompression = attributesCompression
                )
            }
        }
    }

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
        archiveXteaKey: IntArray = XTEA_ZERO_KEY,
        attributesXteaKey: IntArray = XTEA_ZERO_KEY,
        archiveCompression: Compression = Compression.NONE,
        attributesCompression: Compression = Compression.NONE
    ) {
        val archiveBuffer = ByteBuffer.allocate(filesToWrite.values.sumBy { it.data.limit() })
        filesToWrite.values.map { it.data }.forEach { archiveBuffer.put(it) }
        val archive = Archive(
            archiveId,
            nameHash,
            crc(archiveBuffer),
            null,
            whirlPoolHash(archiveBuffer.array()),
            ArchiveAttributes.Size(null, archiveBuffer.limit()),
            archiveVersion,
            filesToWrite
        )
        JagexCache(cacheDir).use { cache ->
            cache.writeArchive(
                dictionaryId, archive, archiveGroupCount, attributesVersion, archiveContainerVersion,
                attributesContainerVersion, archiveXteaKey, attributesXteaKey, archiveCompression, attributesCompression
            )
        }
        // create new cache to remove attributes from memory and read them in again
        JagexCache(cacheDir, mutableMapOf(dictionaryId to attributesXteaKey)).use { cache ->
            val readArchive = cache.readArchive(dictionaryId, archiveId, archiveXteaKey)
            archive.files.values.forEach { it.data.flip() }
            assertEquals(archive, readArchive)
        }
    }

    companion object {
        private val testFiles = mapOf(
            1 to Archive.File(ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} }, null),
            2 to Archive.File(ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} }, null),
            3 to Archive.File(ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} }, null)
        )
    }
}