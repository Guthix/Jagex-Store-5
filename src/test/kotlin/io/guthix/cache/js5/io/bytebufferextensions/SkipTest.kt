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

import io.guthix.cache.js5.io.putMedium
import io.guthix.cache.js5.io.skip
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkipTest {
    @ParameterizedTest
    @MethodSource("testBuffers")
    fun `skip amount`(amount: Int, value: Byte, writable: ByteBuffer, expected: ByteBuffer) {
        writable.skip(amount).put(value)
        Assertions.assertEquals(expected, writable)
    }

    companion object {
        @JvmStatic
        fun testBuffers(): List<Arguments> {
            val testValue = 93.toByte()
            return listOf(
                Arguments.of(3, testValue,
                    ByteBuffer.allocate(6).apply {
                        put(3)
                        put(5)
                    },
                    ByteBuffer.allocate(6).apply {
                        put(3)
                        put(5)
                        putMedium(0) // 3 bytes
                        put(testValue)
                    }
                ),
                Arguments.of(0, testValue,
                    ByteBuffer.allocate(3).apply {
                        put(3)
                        put(5)
                    },
                    ByteBuffer.allocate(3).apply {
                        put(3)
                        put(5)
                        put(testValue)
                    }
                ),
                Arguments.of(-1, 94.toByte(),
                    ByteBuffer.allocate(2).apply {
                        put(3)
                        put(5)
                    },
                    ByteBuffer.allocate(2).apply {
                        put(3)
                        put(testValue)
                    }
                )
            )
        }
    }
}