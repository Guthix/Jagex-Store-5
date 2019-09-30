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
import io.kotlintest.matchers.startWith
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.netty.buffer.Unpooled
import java.lang.IllegalArgumentException
import java.nio.file.Files

class Js5DiskStoreTest : StringSpec() {
    init {
        val fsFolder = Files.createTempDirectory("js5")
        val diskStore = autoClose(Js5DiskStore.open(fsFolder))
        val indexId = 0
        val containerId1 = 0
        val data1 = Unpooled.buffer(34720).iterationFill()
        "After writing and reading the data should be the same as the original" {
            diskStore.write(indexId, containerId1, data1.copy())
            diskStore.read(indexId, containerId1) shouldBe data1
        }

        "Throw exception when creating an non sequential index" {
            val illegalArgument = shouldThrow<IllegalArgumentException> {
                diskStore.write(2, 0, data1)
            }
            illegalArgument.message should startWith("Can not write to or create index file")
        }

        "Throw exception when reading from an index that does not exist" {
            val illegalArgument = shouldThrow<IllegalArgumentException> {
                diskStore.read(1, 0)
            }
            illegalArgument.message should startWith("Can not read from index file")
        }

        val containerId2 = 1
        val data2 = Unpooled.buffer(3865).iterationFill()
        "After writing and reading a second time the data should be the same as the original" {
            diskStore.write(indexId, containerId2, data2)
            diskStore.read(indexId, containerId2) shouldBe data2
        }

        "After overwriting and reading the data should be the same as the overwritten data" {
            diskStore.write(indexId, containerId1, data2)
            diskStore.read(indexId, containerId1) shouldBe data2
        }
    }
}