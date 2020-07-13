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
package io.guthix.cache.js5.container.disk

import io.guthix.cache.js5.iterationFill
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