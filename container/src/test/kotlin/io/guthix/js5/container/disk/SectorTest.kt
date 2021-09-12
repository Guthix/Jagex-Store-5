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
package io.guthix.js5.container.disk

import io.guthix.js5.iterationFill
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled

class SectorTest : StringSpec({
    val indexFileId = 1
    val position = 1
    val containerId = 1
    val normalData = Unpooled.buffer(Sector.DATA_SIZE).iterationFill()
    val sector = Sector(containerId, position, position + 1, indexFileId, normalData)
    val extendedContainerId = 65536
    val extendedData = Unpooled.buffer(Sector.EXTENDED_DATA_SIZE).iterationFill()
    val extendedSector = Sector(extendedContainerId, position, position + 1, indexFileId, extendedData.copy())
    "After encoding and decoding a sector it should be the same as the original" {
        val writeSector = sector.copy()
        Sector.decode(writeSector.containerId, writeSector.encode()) shouldBe sector
    }

    "After encoding and decoding an extended sector it should be the same as the original" {
        val writeSector = extendedSector.copy()
        Sector.decode(writeSector.containerId, writeSector.encode()) shouldBe extendedSector
    }

    "Sectors belonging to containers with an id higher than 65535 should be extended" {
        sector.isExtended.shouldBeFalse()
        extendedSector.isExtended.shouldBeTrue()
    }
})