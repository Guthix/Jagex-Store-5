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

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Security
import java.util.zip.CRC32

internal fun crc(buffer: ByteBuffer): Int {
    val crc = CRC32()
    crc.update(buffer.array())
    return crc.value.toInt()
}

internal const val whirlPoolHashByteCount = 64

internal fun whirlPoolHash(data: ByteArray): ByteArray {
    Security.addProvider(BouncyCastleProvider())
    return MessageDigest.getInstance("Whirlpool").digest(data)
}

const val XTEA_KEY_SIZE = 4

val XTEA_ZERO_KEY = IntArray(XTEA_KEY_SIZE)

private const val XTEA_GOLDEN_RATIO = -0x61c88647

private const val XTEA_ROUNDS = 32

internal fun ByteBuffer.xteaEncrypt(key: IntArray, start: Int, end: Int): ByteBuffer {
    require(key.size == XTEA_KEY_SIZE)
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = 0
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(XTEA_ROUNDS) {
            v0 += (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
            sum += XTEA_GOLDEN_RATIO
            v1 += (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
        }
        putInt(start + i * 8, v0)
        putInt(start + i * 8 + 4, v1)
    }
    return this
}

@Suppress("INTEGER_OVERFLOW")
internal fun ByteBuffer.xteaDecrypt(key: IntArray, start: Int = 0, end: Int = limit()): ByteBuffer {
    require(key.size == XTEA_KEY_SIZE)
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = XTEA_GOLDEN_RATIO * XTEA_ROUNDS
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(XTEA_ROUNDS) {
            v1 -= (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
            sum -= XTEA_GOLDEN_RATIO
            v0 -= (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
        }
        putInt(start + i * 8, v0)
        putInt(start + i * 8 + 4, v1)
    }
    return this
}

internal fun rsaCrypt(data: ByteArray, mod: BigInteger, key: BigInteger) =
    BigInteger(data).modPow(key, mod).toByteArray()