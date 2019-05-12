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
package io.guthix.cache.js5.io.bytebufferextensions

import io.guthix.cache.js5.io.peak
import io.guthix.cache.js5.io.uPeak
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeakTest {
    @ParameterizedTest
    @MethodSource("signedTestValues")
    @ExperimentalUnsignedTypes
    fun `signed peak for correct inputs`(peakValue: Int) {
        val buffer = ByteBuffer.allocate(1).apply { put(peakValue.toByte()) }.flip()
        Assertions.assertEquals(peakValue, buffer.peak().toInt())
    }

    @ParameterizedTest
    @MethodSource("signedFailTestValues")
    @ExperimentalUnsignedTypes
    fun `signed peak for signed overflow inputs`(peakValue: Int) {
        val buffer = ByteBuffer.allocate(1).apply { put(peakValue.toByte()) }.flip()
        Assertions.assertNotEquals(peakValue, buffer.peak().toInt())
    }

    @ParameterizedTest
    @MethodSource("unsignedTestValues")
    @ExperimentalUnsignedTypes
    fun `unsigned peak for correct inputs`(peakValue: Int) {
        val buffer = ByteBuffer.allocate(1).apply { put(peakValue.toByte()) }.flip()
        Assertions.assertEquals(peakValue, buffer.uPeak().toInt())
    }

    companion object {
        @JvmStatic
        fun signedTestValues() = listOf(
            Arguments.of(3),
            Arguments.of(0),
            Arguments.of(127)
        )

        @JvmStatic
        fun signedFailTestValues() = listOf(
            Arguments.of(129),
            Arguments.of(30578),
            Arguments.of(288)
        )

        @JvmStatic
        fun unsignedTestValues() = listOf(
            Arguments.of(3),
            Arguments.of(0),
            Arguments.of(127),
            Arguments.of(128),
            Arguments.of(129),
            Arguments.of(255)
        )
    }
}