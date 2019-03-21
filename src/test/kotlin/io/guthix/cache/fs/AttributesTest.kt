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
package io.guthix.cache.fs

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class AttributesTest {
    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    fun encodeDecodeTest(dictAttr: DictionaryAttributes, containerVersion: Int) {
        Assertions.assertEquals(
            dictAttr,
            DictionaryAttributes.decode(
                Container(containerVersion, dictAttr.encode())
            )
        )
    }

    companion object {
        @JvmStatic
        fun encodeDecodeTestArgs() = listOf(
            Arguments.of(
                DictionaryAttributes(
                    version = 10,
                    archiveAttributes = mutableMapOf(
                        1 to ArchiveAttributes(
                            id = 1,
                            nameHash = null,
                            crc = 231231,
                            unknownHash = null,
                            whirlpoolHash = null,
                            sizes = null,
                            version = 10,
                            fileAttributes = mutableMapOf(
                                1 to FileAttributes(id = 1, nameHash = null),
                                2 to FileAttributes(id = 2, nameHash = null)
                            )
                        ),
                        2 to ArchiveAttributes(
                            id = 2,
                            nameHash = null,
                            crc = 58529,
                            unknownHash = null,
                            whirlpoolHash = null,
                            sizes = null,
                            version = 3,
                            fileAttributes = mutableMapOf(
                                1 to FileAttributes(id = 1, nameHash = null),
                                2 to FileAttributes(id = 2, nameHash = null)
                            )
                        )
                    )
                ),
                3
            )
        )
    }
}