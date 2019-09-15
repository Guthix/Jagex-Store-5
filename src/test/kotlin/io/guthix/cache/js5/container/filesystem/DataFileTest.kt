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
package io.guthix.cache.js5.container.filesystem

import io.netty.buffer.Unpooled
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private val logger = KotlinLogging.logger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataFileTest {
    @ParameterizedTest
    @MethodSource("testNormalSegment")
    internal fun `Encode and decode compare normal segment`(segment: Segment) {
        assertEquals(segment, Segment.decode(segment.copy().encode()))
    }

    @ParameterizedTest
    @MethodSource("testExtendedSegment")
    internal fun `Encode and decode compare extended segment`(segment: Segment) {
        assertEquals(segment, Segment.decodeExtended(segment.copy().encode()))
    }

    companion object {
        @JvmStatic
        fun testNormalSegment() = listOf(
            Arguments.of(
                Segment(
                    indexFileId = 1,
                    containerId = 1,
                    position = 1,
                    nextSegmentNumber = 2,
                    data = Unpooled.buffer(Segment.DATA_SIZE).apply {
                        writerIndex(Segment.DATA_SIZE)
                    }
                )
            )
        )

        @JvmStatic
        fun testExtendedSegment() = listOf(
            Arguments.of(
                Segment(
                    indexFileId = 1,
                    containerId = 65536,
                    position = 1,
                    nextSegmentNumber = 2,
                    data = Unpooled.buffer(Segment.EXTENDED_DATA_SIZE).apply {
                        writerIndex(Segment.EXTENDED_DATA_SIZE)
                    }
                )
            )
        )
    }
}