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
package io.guthix.cache.fs.util

import java.math.BigInteger
import java.nio.ByteBuffer

object XTEA {
    const val GOLDEN_RATIO = -0x61c88647
    const val ROUNDS = 32
    const val KEY_SIZE = 4
    val ZERO_KEY = IntArray(KEY_SIZE)
}

fun ByteBuffer.xteaEncrypt(key: IntArray, start: Int, end: Int): ByteBuffer {
    require(key.size == XTEA.KEY_SIZE)
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = 0
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(XTEA.ROUNDS) {
            v0 += (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
            sum += XTEA.GOLDEN_RATIO
            v1 += (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
        }
        putInt(start + i * 8, v0)
        putInt(start + i * 8 + 4, v1)
    }
    return this
}

@Suppress("INTEGER_OVERFLOW")
fun ByteBuffer.xteaDecrypt(key: IntArray, start: Int = 0, end: Int = limit()): ByteBuffer {
    require(key.size == XTEA.KEY_SIZE)
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = XTEA.GOLDEN_RATIO * XTEA.ROUNDS
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(XTEA.ROUNDS) {
            v1 -= (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
            sum -= XTEA.GOLDEN_RATIO
            v0 -= (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
        }
        putInt(start + i * 8, v0)
        putInt(start + i * 8 + 4, v1)
    }
    return this
}

fun rsaCrypt(data: ByteArray, mod: BigInteger, key: BigInteger) = BigInteger(data).modPow(key, mod).toByteArray()

