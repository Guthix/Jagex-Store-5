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
package io.guthix.cache.js5.container.filesystem

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Js5FileSystemTest {
    @Test
    @ExperimentalUnsignedTypes
    fun `Write and read compare file`(@TempDir cacheDir: File) {
        Js5FileSystem(cacheDir).use { fs ->
            val dataToWrite = ByteBuffer.allocate(20).apply {
                repeat(20) { put(it.toByte())}
            }.flip()
            fs.write(0, 1, dataToWrite)
            val readData = fs.read(0, 1)
            assertEquals(dataToWrite.flip(), readData)
        }
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Write, overwrite and read compare file`(@TempDir cacheDir: File) {
        Js5FileSystem(cacheDir).use { fs ->
            val dataToWrite = ByteBuffer.allocate(20).apply {
                repeat(20) { put(it.toByte())}
            }.flip()
            val dataToOverWrite = ByteBuffer.allocate(20).apply {
                repeat(20) { put((2 * it).toByte())}
            }.flip()
            fs.write(0, 1, dataToWrite)
            fs.write(0, 1, dataToOverWrite)
            val readData = fs.read(0, 1)
            assertEquals(dataToOverWrite.flip(), readData)
        }
    }

    @Test
    @ExperimentalUnsignedTypes
    fun `Throws exception when indexes are non consecutive`(@TempDir cacheDir: File) {
        Js5FileSystem(cacheDir).use { fs ->
            val dataToWrite = ByteBuffer.allocate(20).apply {
                repeat(20) { put(it.toByte())}
            }.flip()
            Assertions.assertThrows(IOException::class.java) {
                fs.write(1, 1, dataToWrite)
            }
        }
    }
}