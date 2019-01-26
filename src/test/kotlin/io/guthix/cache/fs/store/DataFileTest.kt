/*
GNU LGPL V3
Copyright (C) 2019 Bart van Helvert
B.A.J.v.Helvert@gmail.com

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.fs.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SegmentTest {
    @ParameterizedTest
    @MethodSource("encodeDecodeNormalTestArgs")
    @ExperimentalUnsignedTypes
    internal fun encodeDecodeNormalTest(segment: Segment)=
        assertEquals(segment, Segment.decode(segment.encode()))

    @ParameterizedTest
    @MethodSource("encodeDecodeExtendedTestArgs")
    @ExperimentalUnsignedTypes
    internal fun encodeDecodeExtendedTest(segment: Segment)=
        assertEquals(segment, Segment.decodeExtended(segment.encode()))

    companion object {
        @JvmStatic
        @ExperimentalUnsignedTypes
        fun encodeDecodeNormalTestArgs() = listOf(
            Arguments.of(
                Segment(
                    indexFileId = 1.toUByte(),
                    containerId = 1,
                    segmentPart = 1.toUShort(),
                    nextSegmentPos = 2,
                    data = ByteArray(Segment.DATA_SIZE)
                )
            )
        )

        @JvmStatic
        @ExperimentalUnsignedTypes
        fun encodeDecodeExtendedTestArgs() = listOf(
            Arguments.of(
                Segment(
                    indexFileId = 1.toUByte(),
                    containerId = UShort.MAX_VALUE.toInt() + 1,
                    segmentPart = 1.toUShort(),
                    nextSegmentPos = 2,
                    data = ByteArray(Segment.EXTENDED_DATA_SIZE)
                )
            )
        )
    }
}