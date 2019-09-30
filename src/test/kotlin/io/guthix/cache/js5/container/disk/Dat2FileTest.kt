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
package io.guthix.cache.js5.container.disk

import io.guthix.cache.js5.iterationFill
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.netty.buffer.Unpooled
import java.nio.file.Files
import kotlin.math.ceil

class Dat2FileTest : StringSpec() {
    init {
        val dataFile = autoClose(Dat2File.open(Files.createTempFile("main_file_cache", "dat2")))
        val indexFile = 1
        val containerId1= 0
        val dataSize1 = 583027
        val data1 = Unpooled.buffer(dataSize1).iterationFill()
        "After writing and reading the data should be the same as the original" {
            val index = Index(dataSize1, 0)
            dataFile.write(indexFile, containerId1, index, data1.copy())
            dataFile.read(indexFile, containerId1, index) shouldBe data1
        }

        val containerId2 = 1
        val dataSize2 = 7835
        "After writing and reading a second container the data should be the same as the original" {
            val index = Index(dataSize2, ceil(dataFile.size.toDouble() / Sector.SIZE).toInt())
            val data = Unpooled.buffer(dataSize2).iterationFill()
            dataFile.write(indexFile, containerId2, index, data.copy())
            dataFile.read(indexFile, containerId2, index) shouldBe data
        }

        val containerId3 = 2
        val dataSize3 = 299
        "After writing and reading a small data sector the data should be the same as the original" {
            val index = Index(dataSize3, ceil(dataFile.size.toDouble() / Sector.SIZE).toInt())
            val data = Unpooled.buffer(dataSize3).iterationFill()
            dataFile.write(indexFile, containerId3, index, data.copy())
            dataFile.read(indexFile, containerId3, index) shouldBe data
        }

        val containerId4 = 65536
        "After writing and reading the data should be the same as the original while using extended sectors" {
            val index = Index(dataSize1, ceil(dataFile.size.toDouble() / Sector.SIZE).toInt())
            dataFile.write(indexFile, containerId4, index, data1.copy())
            dataFile.read(indexFile, containerId4, index) shouldBe data1
        }
    }
}