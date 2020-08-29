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
package io.guthix.cache.js5.container.disk

import io.guthix.cache.js5.iterationFill
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled
import java.nio.file.Files

class Js5DiskStoreTest : StringSpec() {
    init {
        val fsFolder = Files.createTempDirectory("js5")
        val diskStore = autoClose(Js5DiskStore.open(fsFolder))
        val containerId1 = 0
        val data1 = Unpooled.buffer(34720).iterationFill()
        "Writing data" {
            diskStore.write(0, containerId1, data1.copy())
            diskStore.read(0, containerId1) shouldBe data1
        }

        val containerId2 = 1
        val data2 = Unpooled.buffer(3865).iterationFill()
        "Writing data after another write" {
            diskStore.write(0, containerId2, data2)
            diskStore.read(0, containerId2) shouldBe data2
        }

        "Overwriting data smaller than the original data" {
            diskStore.write(0, containerId1, data2)
            diskStore.read(0, containerId1) shouldBe data2
        }

        val data3 = Unpooled.buffer(39000).iterationFill()
        "Overwriting data bigger than the original data" {
            diskStore.write(0, containerId1, data3)
            diskStore.read(0, containerId1) shouldBe data3
        }

        val data4 = Unpooled.buffer(54305).iterationFill()
        "Overwriting data bigger than the original data after another overwrite" {
            diskStore.write(0, containerId2, data4)
            diskStore.read(0, containerId2) shouldBe data4
        }
    }
}