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
package io.guthix.cache.js5.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataFileTest {
    @ParameterizedTest
    @MethodSource("testNormalSegment")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode compare normal segment`(segment: Segment) =
        assertEquals(segment, Segment.decode(segment.encode()))

    @ParameterizedTest
    @MethodSource("testExtendedSegment")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode compare extended segment`(segment: Segment) =
        assertEquals(segment, Segment.decodeExtended(segment.encode()))

    companion object {
        @JvmStatic
        @ExperimentalUnsignedTypes
        fun testNormalSegment() = listOf(
            Arguments.of(
                Segment(
                    indexFileId = 1.toUByte(),
                    containerId = 1,
                    position = 1.toUShort(),
                    nextSegmentNumber = 2,
                    data = ByteArray(Segment.DATA_SIZE)
                )
            )
        )

        @JvmStatic
        @ExperimentalUnsignedTypes
        fun testExtendedSegment() = listOf(
            Arguments.of(
                Segment(
                    indexFileId = 1.toUByte(),
                    containerId = UShort.MAX_VALUE.toInt() + 1,
                    position = 1.toUShort(),
                    nextSegmentNumber = 2,
                    data = ByteArray(Segment.EXTENDED_DATA_SIZE)
                )
            )
        )
    }
}