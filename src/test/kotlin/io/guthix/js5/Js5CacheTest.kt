/*
 * Copyright 2018-2021 Guthix
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
package io.guthix.js5

import io.guthix.js5.container.disk.Js5DiskStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled

class Js5CacheTest : StringSpec({
    val diskStore = autoClose(Js5DiskStore.open(createEmptyCacheFolder()))
    val cache = Js5Cache(diskStore)
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
        uncompressedCrc = 2390324
    )
    "After reading and writing the group should be the same as the original" {
        val archive = cache.addArchive(version = 0, containsWpHash = true, containsSizes = true,
            containsUnknownHash = true, containsNameHash = true
        )
        archive.writeGroup(group, autoVersion = true)
        cache.writeArchive(archive)
        val readGroup = cache.readArchive(0).readGroup(group.id)
        readGroup shouldBe group
        val readArchive = cache.readArchive(archive.id)
        readArchive shouldBe archive
    }
})