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

import io.guthix.cache.js5.util.Js5Compression
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Js5ContainerTest {
    @ParameterizedTest
    @MethodSource("testContainer")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode container no encryption no compression`(js5Container: Js5Container) =
        Assertions.assertEquals(js5Container, Js5Container.decode(js5Container.encode(Js5Compression.NONE)))

    @ParameterizedTest
    @MethodSource("testContainer")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode container no encryption GZIP compression`(js5Container: Js5Container) =
        Assertions.assertEquals(js5Container, Js5Container.decode(js5Container.encode(Js5Compression.GZIP)))

    @ParameterizedTest
    @MethodSource("testContainer")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode container no encryption BZIP compression`(js5Container: Js5Container) =
        Assertions.assertEquals(js5Container, Js5Container.decode(js5Container.encode(Js5Compression.BZIP2)))

    @ParameterizedTest
    @MethodSource("testContainer")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode container no encryption LZMA compression`(js5Container: Js5Container) =
        Assertions.assertEquals(js5Container, Js5Container.decode(js5Container.encode(Js5Compression.LZMA)))

    @ParameterizedTest
    @MethodSource("testContainer")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode container XTEA encryption no compression`(js5Container: Js5Container) =
        Assertions.assertEquals(js5Container,
            Js5Container.decode(js5Container.encode(Js5Compression.NONE, xteaKey), xteaKey)
        )

    @ParameterizedTest
    @MethodSource("testContainer")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode container XTEA encryption GZIP compression`(js5Container: Js5Container) =
        Assertions.assertEquals(js5Container,
            Js5Container.decode(js5Container.encode(Js5Compression.GZIP, xteaKey), xteaKey)
        )


    @ParameterizedTest
    @MethodSource("testContainer")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode container XTEA encryption BZIP compression`(js5Container: Js5Container) =
        Assertions.assertEquals(js5Container,
            Js5Container.decode(js5Container.encode(Js5Compression.BZIP2, xteaKey), xteaKey)
        )

    @ParameterizedTest
    @MethodSource("testContainer")
    @ExperimentalUnsignedTypes
    internal fun `Encode and decode container XTEA encryption LZMA compression`(js5Container: Js5Container) =
        Assertions.assertEquals(js5Container,
            Js5Container.decode(js5Container.encode(Js5Compression.LZMA, xteaKey), xteaKey)
        )

    companion object {
        val xteaKey = intArrayOf(376495908, 4927, 37654959, 936549)

        @JvmStatic
        @ExperimentalUnsignedTypes
        fun testContainer() = listOf(
            Arguments.of(Js5Container(-1, ByteBuffer.allocate(8).apply {
                put(8)
                put(3)
                putShort(4)
                putInt(8)
            }.flip())),
            Arguments.of(Js5Container(10, ByteBuffer.allocate(8).apply {
                put(-1)
                put(10)
                putShort(30)
                putInt(900)
            }.flip()))
        )
    }
}