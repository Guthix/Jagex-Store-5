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
package io.guthix.cache.fs.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

class XTEATest {
    @ParameterizedTest
    @MethodSource("encryptDecryptTestArgs")
    fun encryptDecryptTest(data: ByteBuffer, keySet: IntArray) {
        val encrypted = data.xteaEncrypt(keySet,0, data.limit())
        val decrypted = encrypted.xteaDecrypt(keySet, 0, data.limit())
        Assertions.assertEquals(data, decrypted)
    }

    companion object {
        @JvmStatic
        fun encryptDecryptTestArgs()  = listOf(
            Arguments.of(
                ByteBuffer.allocate(8).apply {
                    put(8)
                    put(3)
                    putShort(4)
                    putInt(8)
                }.flip(),
                intArrayOf(586096, 984665, 1856, 578569)
            ),
            Arguments.of(
                ByteBuffer.allocate(12).apply {
                    put(32)
                    put(56)
                    putShort(5876)
                    putInt(95031)
                    putInt(3294765)
                }.flip(),
                intArrayOf(0, 0, 0, 0)
            )
        )
    }
}