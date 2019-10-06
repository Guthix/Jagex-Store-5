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
package io.guthix.cache.js5.container

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer

/**
 * The amount of [Int] keys in a XTEA key.
 */
const val XTEA_KEY_SIZE = 4

/**
 * A 0 XTEA key.
 */
val XTEA_ZERO_KEY = IntArray(XTEA_KEY_SIZE)

/**
 * The XTEA golden ratio.
 */
private const val GOLDEN_RATIO = -0x61c88647

/**
 * Amount of encryption rounds.
 */
private const val ROUNDS = 32

/**
 * Encrypts a [ByteBuf] using XTEA encryption.
 */
@Suppress("MagicNumber")
internal fun ByteBuf.xteaEncrypt(key: IntArray, start: Int = 0, end: Int = capacity()): ByteBuf {
    require(key.size == XTEA_KEY_SIZE) { "The XTEA key should be 128 byte long." }
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = 0
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(ROUNDS) {
            v0 += (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
            sum += GOLDEN_RATIO
            v1 += (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
        }
        setInt(start + i * 8, v0)
        setInt(start + i * 8 + 4, v1)
    }
    return this
}


/**
 * Decrypts a [ByteBuf] using XTEA encryption.
 */
@Suppress("INTEGER_OVERFLOW")
internal fun ByteBuf.xteaDecrypt(key: IntArray, start: Int = 0, end: Int = capacity()): ByteBuf {
    require(key.size == XTEA_KEY_SIZE) { "The XTEA key should be 128 byte long." }
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = GOLDEN_RATIO * ROUNDS
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(ROUNDS) {
            v1 -= (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
            sum -= GOLDEN_RATIO
            v0 -= (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
        }
        setInt(start + i * 8, v0)
        setInt(start + i * 8 + 4, v1)
    }
    return this
}