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
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Js5GroupTest {
    @ParameterizedTest
    @MethodSource("testGroups")
    fun `Encode and decode compare group`(group: Js5Group, segmentCount: Int) {
        val fileSettings = mutableMapOf<Int, Js5FileSettings>()
        group.files.forEach { (fileId, file) ->
            fileSettings[fileId] = Js5FileSettings(fileId, file.nameHash)
        }
        Assertions.assertEquals(group,
            Js5Group.decode(
                group.encode(segmentCount),
                Js5GroupSettings(
                    group.id,
                    group.nameHash,
                    group.crc,
                    group.unknownHash,
                    group.whirlpoolHash,
                    group.sizes,
                    group.version,
                    fileSettings
                )
            )
        )
    }

    companion object {
        @JvmStatic
        fun testGroups(): List<Arguments> {
            val data1 = ByteBuffer.allocate(8).apply {
                put(8)
                put(3)
                putShort(4)
                putInt(8)
            }.array()
            val data2 = ByteBuffer.allocate(8).apply {
                put(20)
                put(0)
                putShort(24854)
                putInt(93432)
            }.array()
            val encodeData = mutableMapOf(
                1 to Js5Group.File(null, data1),
                2 to Js5Group.File(null, data2)
            )
            return listOf(
                Arguments.of( // group = 1 test
                    Js5Group(
                        id = 1,
                        nameHash = null,
                        crc = 231231,
                        unknownHash = null,
                        whirlpoolHash = null,
                        sizes = null,
                        version = 10,
                        files = encodeData
                    ),
                    1 // groupCount
                ),
                Arguments.of( // group = 8 test
                    Js5Group(
                        id = 1,
                        nameHash = null,
                        crc = 231231,
                        unknownHash = null,
                        whirlpoolHash = null,
                        sizes = null,
                        version = 10,
                        files = encodeData
                    ),
                    8 // groupCount
                ),
                Arguments.of( // version = -1 test
                    Js5Group(
                        id = 1,
                        nameHash = null,
                        crc = 231231,
                        unknownHash = null,
                        whirlpoolHash = null,
                        sizes = null,
                        version = 10,
                        files = encodeData
                    ),
                    1 // groupCount
                )
            )
        }
    }
}