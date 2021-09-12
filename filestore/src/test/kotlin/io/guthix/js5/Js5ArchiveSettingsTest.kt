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

import io.guthix.js5.container.Js5Container
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class Js5ArchiveSettingsTest : StringSpec({
    val archiveSettings = Js5ArchiveSettings(
        version = 13,
        groupSettings = sortedMapOf(
            0 to Js5GroupSettings(
                id = 0,
                version = 2,
                compressedCrc = 284576,
                fileSettings = sortedMapOf(
                    0 to Js5FileSettings(id = 0, nameHash = 349435435),
                    1 to Js5FileSettings(id = 1, nameHash = 856381673),
                    3 to Js5FileSettings(id = 3, nameHash = 725662303)
                ),
                nameHash = 1834572,
                uncompressedCrc = 393243,
                whirlpoolHash = ByteArray(WHIRLPOOL_HASH_SIZE),
                sizes = Js5Container.Size(287, 537)
            ),
            1 to Js5GroupSettings(
                id = 1,
                version = 8,
                compressedCrc = 46739,
                fileSettings = sortedMapOf(
                    0 to Js5FileSettings(id = 0, nameHash = 4345435),
                    1 to Js5FileSettings(id = 1, nameHash = 8342373),
                    2 to Js5FileSettings(id = 2, nameHash = 6544235)
                ),
                nameHash = 23432,
                uncompressedCrc = 3254325,
                whirlpoolHash = ByteArray(WHIRLPOOL_HASH_SIZE),
                sizes = Js5Container.Size(4995, 5869)
            ),
            3 to Js5GroupSettings(
                id = 3,
                version = 1,
                compressedCrc = 32312,
                fileSettings = sortedMapOf(
                    0 to Js5FileSettings(id = 0, nameHash = 2140124),
                    1 to Js5FileSettings(id = 1, nameHash = 6741242),
                    2 to Js5FileSettings(id = 2, nameHash = 3252517)
                ),
                nameHash = 4659,
                uncompressedCrc = 84327,
                whirlpoolHash = ByteArray(WHIRLPOOL_HASH_SIZE),
                sizes = Js5Container.Size(87, 158)
            )
        ), containsNameHash = true, containsWpHash = true, containsSizes = true, containsUncompressedCrc = true
    )

    "After encoding and decoding the settings should be the same as the original" {
        Js5ArchiveSettings.decode(archiveSettings.encode()) shouldBe archiveSettings
    }
})