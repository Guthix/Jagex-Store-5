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
 * along with Guthix Jagex-Store-5. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5

import io.guthix.cache.js5.container.disk.Js5DiskStore
import io.kotest.core.spec.autoClose
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled
import java.nio.file.Files

class Js5CacheTest : StringSpec() {
    init {
        val fsFolder = Files.createTempDirectory("js5")
        val diskStore = Js5DiskStore.open(fsFolder)
        val cache = autoClose(Js5Cache(diskStore))
        val files = mutableMapOf(
            0 to Js5File(0, 23482, Unpooled.buffer(390).iterationFill()),
            1 to Js5File(1, 5234, Unpooled.buffer(823).iterationFill()),
            2 to Js5File(2, 6536, Unpooled.buffer(123).iterationFill())
        )
        val group = Js5Group(
            id = 0,
            version = 23,
            chunkCount = 10,
            files = files,
            nameHash = 3489234,
            unknownHash = 2390324
        )
        "After reading and writing the group should be the same as the original" {
            val archive = cache.addArchive(version = 0, containsWpHash = true, containsSizes = true,
                containsUnknownHash = true, containsNameHash = true
            )
            archive.writeGroup(group, true)
            cache.writeArchive(archive)
            val readGroup = cache.readArchive(0).readGroup(group.id)
            readGroup shouldBe group
            val readArchive = cache.readArchive(archive.id)
            readArchive shouldBe archive
        }
    }
}