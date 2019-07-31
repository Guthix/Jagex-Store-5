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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Js5MasterTest {
    @ParameterizedTest
    @MethodSource("testSettings")
    @ExperimentalUnsignedTypes
    fun `Encode and decode compare archive settings`(archiveSettings: Js5ArchiveSettings) {
        Assertions.assertEquals(
            archiveSettings,
            Js5ArchiveSettings.decode(
                archiveSettings.encode()
            )
        )
    }

    companion object {
        @JvmStatic
        fun testSettings() = listOf(
            Arguments.of(
                Js5ArchiveSettings(
                    version = 10,
                    js5GroupSettings = mutableMapOf(
                        1 to Js5GroupSettings(
                            id = 1,
                            nameHash = null,
                            crc = 231231,
                            unknownHash = null,
                            whirlpoolHash = null,
                            sizes = null,
                            version = 10,
                            fileSettings = mutableMapOf(
                                1 to Js5FileSettings(id = 1, nameHash = null),
                                2 to Js5FileSettings(id = 2, nameHash = null)
                            )
                        ),
                        2 to Js5GroupSettings(
                            id = 2,
                            nameHash = null,
                            crc = 58529,
                            unknownHash = null,
                            whirlpoolHash = null,
                            sizes = null,
                            version = 3,
                            fileSettings = mutableMapOf(
                                1 to Js5FileSettings(id = 1, nameHash = null),
                                2 to Js5FileSettings(id = 2, nameHash = null)
                            )
                        )
                    )
                )
            )
        )
    }
}