/*
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5.container.disk

import io.guthix.cache.js5.iterationFill
import io.kotest.core.spec.autoClose
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