/*
 * Copyright 2018-2020 Guthix
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.guthix.js5.container.disk

import io.guthix.js5.iterationFill
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled
import java.nio.file.Files
import kotlin.math.ceil

class Dat2FileTest : StringSpec() {
    init {
        val dataFile = autoClose(Dat2File.open(Files.createTempFile("main_file_cache", "dat2")))
        val indexFile = 1
        val containerId1 = 0
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