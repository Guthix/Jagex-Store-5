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
package io.guthix.cache.js5.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XTEATest {
    @ParameterizedTest
    @MethodSource("testBuffers")
    fun `Encrypt and decrypt compare data`(data: ByteBuf, keySet: IntArray) {
        val encrypted = data.xteaEncrypt(keySet,0, data.capacity())
        val decrypted = Unpooled.wrappedBuffer(encrypted).xteaDecrypt(keySet, 0, data.capacity())
        Assertions.assertEquals(data, decrypted)
    }

    companion object {
        @JvmStatic
        fun testBuffers()  = listOf(
            Arguments.of(
                Unpooled.buffer(8).apply {
                    writeByte(8)
                    writeByte(3)
                    writeShort(4)
                    writeInt(8)
                },
                intArrayOf(586096, 984665, 1856, 578569)
            ),
            Arguments.of(
                Unpooled.buffer(12).apply {
                    writeByte(32)
                    writeByte(56)
                    writeShort(5876)
                    writeInt(95031)
                    writeInt(3294765)
                },
                intArrayOf(0, 0, 0, 0)
            )
        )
    }
}