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
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
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