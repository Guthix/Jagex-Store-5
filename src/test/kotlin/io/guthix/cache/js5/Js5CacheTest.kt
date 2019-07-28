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
package io.guthix.cache.js5

import io.guthix.cache.js5.container.filesystem.Js5FileSystem
import io.guthix.cache.js5.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Js5CacheTest {
    @Test
    @ExperimentalUnsignedTypes
    fun `Read write compare group with sequential file ids`(@TempDir cacheDir: File) {
        readWriteTest(cacheDir, testFiles)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Read write compare group with non sequential file ids`(@TempDir cacheDir: File) {
        val nonSeqTestFiles = mapOf(
            1 to Js5Group.File(ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} }, null),
            6 to Js5Group.File(ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} }, null),
            10 to Js5Group.File(ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} }, null)
        )
        readWriteTest(cacheDir, nonSeqTestFiles)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Read write compare group with named archive and files`(@TempDir cacheDir: File) {
        val seqNameTestFiles = mapOf(
            1 to Js5Group.File(ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} },
                "SeqTest1".hashCode()
            ),
            2 to Js5Group.File(ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} },
                "SeqTest2".hashCode()
            ),
            3 to Js5Group.File(ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} },
                "SeqTest3".hashCode()
            )
        )
        readWriteTest(cacheDir, seqNameTestFiles, nameHash = "Hello World!".hashCode())
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Read write compare group in multiple segments`(@TempDir cacheDir: File) {
        val testSegments = listOf(1, 3, 8, 10, 20)
        testSegments.forEach { readWriteTest(cacheDir, testFiles, groupSegmentCount = it) }
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Read write compare group with manually providing versions`(@TempDir cacheDir: File) {
        readWriteTest(cacheDir, testFiles, groupVersion = 3, settingsVersion = 8)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Read write compare group with manually providing container versions`(@TempDir cacheDir: File) {
        readWriteTest(cacheDir, testFiles, groupContainerVersion = 3, settingsContainerVersion = 8)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Read write compare encrypted group`(@TempDir cacheDir: File) {
        val groupXteaKey = intArrayOf(3028, 1, 759, 43945)
        val settingsXteaKey = intArrayOf(895, 3458790, 4358976, 32470)
        readWriteTest(cacheDir, testFiles, groupXteaKey = groupXteaKey, settingsXteaKey = settingsXteaKey)
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Read write compare compressed group`(@TempDir cacheDir: File) {
        Js5Compression.values().forEach { groupCompression ->
            Js5Compression.values().forEach { settingsCompression ->
                readWriteTest(
                    cacheDir,
                    testFiles,
                    groupJs5Compression = groupCompression,
                    settingsJs5Compression = settingsCompression
                )
            }
        }
    }

    @ExperimentalUnsignedTypes
    private fun readWriteTest(
        cacheDir: File,
        filesToWrite: Map<Int, Js5Group.File>,
        archiveId: Int = 0,
        groupId: Int = 0,
        nameHash: Int? = null,
        groupSegmentCount: Int = 1,
        groupVersion: Int = -1,
        settingsVersion: Int? = null,
        groupContainerVersion: Int = -1,
        settingsContainerVersion: Int = -1,
        groupXteaKey: IntArray = XTEA_ZERO_KEY,
        settingsXteaKey: IntArray = XTEA_ZERO_KEY,
        groupJs5Compression: Js5Compression = Js5Compression.NONE,
        settingsJs5Compression: Js5Compression = Js5Compression.NONE
    ) {
        val archiveBuffer = ByteBuffer.allocate(filesToWrite.values.sumBy { it.data.limit() })
        filesToWrite.values.map { it.data }.forEach { archiveBuffer.put(it) }
        val archive = Js5Group(
            groupId,
            nameHash,
            crc(archiveBuffer),
            null,
            whirlPoolHash(archiveBuffer.array()),
            Js5GroupSettings.Size(0, archiveBuffer.limit()),
            groupVersion,
            filesToWrite
        )
        val fs = Js5FileSystem(cacheDir)
        Js5Cache(readerWriter = fs).use { cache ->
            cache.writeGroup(
                archiveId, archive, groupSegmentCount, settingsVersion, groupContainerVersion,
                settingsContainerVersion, groupXteaKey, settingsXteaKey, groupJs5Compression, settingsJs5Compression
            )
        }
        val fs2 = Js5FileSystem(cacheDir) // need to create a new filesystem because fs closed
        // create new cache to remove settings from memory and read them in again
        Js5Cache(readerWriter = fs2, settingsXtea = mutableMapOf(archiveId to settingsXteaKey)).use { cache ->
            val readArchive = cache.readGroup(archiveId, groupId, groupXteaKey)
            archive.files.values.forEach { it.data.flip() }
            assertEquals(archive, readArchive)
        }
    }

    companion object {
        private val testFiles = mapOf(
            1 to Js5Group.File(ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} }, null),
            2 to Js5Group.File(ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} }, null),
            3 to Js5Group.File(ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} }, null)
        )
    }
}