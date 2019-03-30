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
package io.guthix.cache.fs.io.bytebufferextensions

import io.guthix.cache.fs.io.peak
import io.guthix.cache.fs.io.uPeak
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeakTest {
    @ParameterizedTest
    @MethodSource("signedPeakTestEquals")
    @ExperimentalUnsignedTypes
    fun signedPeakTestEquals(peakValue: Int) {
        val buffer = ByteBuffer.allocate(1).apply { put(peakValue.toByte()) }.flip()
        Assertions.assertEquals(peakValue, buffer.peak().toInt())
    }

    @ParameterizedTest
    @MethodSource("signedPeakTestNotEquals")
    @ExperimentalUnsignedTypes
    fun signedPeakTestNotEquals(peakValue: Int) {
        val buffer = ByteBuffer.allocate(1).apply { put(peakValue.toByte()) }.flip()
        Assertions.assertNotEquals(peakValue, buffer.peak().toInt())
    }

    @ParameterizedTest
    @MethodSource("unsignedPeakTestEquals")
    @ExperimentalUnsignedTypes
    fun unsignedPeakTestEquals(peakValue: Int) {
        val buffer = ByteBuffer.allocate(1).apply { put(peakValue.toByte()) }.flip()
        Assertions.assertEquals(peakValue, buffer.uPeak().toInt())
    }

    companion object {
        @JvmStatic
        fun signedPeakTestEquals() = listOf(
            Arguments.of(3),
            Arguments.of(0),
            Arguments.of(127)
        )

        @JvmStatic
        fun signedPeakTestNotEquals() = listOf(
            Arguments.of(129),
            Arguments.of(30578),
            Arguments.of(288)
        )

        @JvmStatic
        fun unsignedPeakTestEquals() = listOf(
            Arguments.of(3),
            Arguments.of(0),
            Arguments.of(127),
            Arguments.of(128),
            Arguments.of(129),
            Arguments.of(255)
        )
    }
}