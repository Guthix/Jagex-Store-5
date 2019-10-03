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
package io.guthix.cache.js5

import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.util.WHIRLPOOL_HASH_SIZE
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class Js5ArchiveSettingsTest : StringSpec({
    val archiveSettings = Js5ArchiveSettings(
        version = 13,
        groupSettings = mutableMapOf(
            0 to Js5GroupSettings(
                id = 0,
                version = 2,
                crc = 284576,
                fileSettings = mutableMapOf(
                    0 to Js5FileSettings(id = 0, nameHash = 349435435),
                    1 to Js5FileSettings(id = 1, nameHash = 856381673),
                    3 to Js5FileSettings(id = 3, nameHash = 725662303)
                ),
                nameHash = 1834572,
                unknownHash = 393243,
                whirlpoolHash = ByteArray(WHIRLPOOL_HASH_SIZE),
                sizes = Js5Container.Size(287, 537)
            ),
            1 to Js5GroupSettings(
                id = 1,
                version = 8,
                crc = 46739,
                fileSettings = mutableMapOf(
                    0 to Js5FileSettings(id = 0, nameHash = 4345435),
                    1 to Js5FileSettings(id = 1, nameHash = 8342373),
                    2 to Js5FileSettings(id = 2, nameHash = 6544235)
                ),
                nameHash = 23432,
                unknownHash = 3254325,
                whirlpoolHash = ByteArray(WHIRLPOOL_HASH_SIZE),
                sizes = Js5Container.Size(4995, 5869)
            ),
            3 to Js5GroupSettings(
                id = 3,
                version = 1,
                crc = 32312,
                fileSettings = mutableMapOf(
                    0 to Js5FileSettings(id = 0, nameHash = 2140124),
                    1 to Js5FileSettings(id = 1, nameHash = 6741242),
                    2 to Js5FileSettings(id = 2, nameHash = 3252517)
                ),
                nameHash = 4659,
                unknownHash = 84327,
                whirlpoolHash = ByteArray(WHIRLPOOL_HASH_SIZE),
                sizes = Js5Container.Size(87, 158)
            )
        ), containsNameHash = true, containsWpHash = true, containsSizes = true, containsUnknownHash = true
    )

    "After encoding and decoding the settings should be the same as the original" {
        Js5ArchiveSettings.decode(archiveSettings.encode()) shouldBe archiveSettings
    }
})