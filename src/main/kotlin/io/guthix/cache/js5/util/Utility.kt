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

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Security
import java.util.zip.CRC32

/**
 * Calculates the [java.util.zip.CRC32] of a [ByteArray].
 */
fun ByteArray.crc(): Int {
    val crc = CRC32()
    crc.update(this)
    return crc.value.toInt()
}

/**
 * The amount of bytes in a whirlpool hash.
 */
internal const val WHIRLPOOL_HASH_SIZE = 64

/**
 * Calculates the whirlpool hash for a [ByteArray].
 */
internal fun ByteArray.whirlPoolHash(): ByteArray {
    Security.addProvider(BouncyCastleProvider())
    return MessageDigest.getInstance("Whirlpool").digest(this)
}

/**
 * The amount of [Int] keys in a XTEA key.
 */
const val XTEA_KEY_SIZE = 4

/**
 * An XTEA key filled with 0s.
 */
val XTEA_ZERO_KEY = IntArray(XTEA_KEY_SIZE)

/**
 * The XTEA golden ratio.
 */
private const val XTEA_GOLDEN_RATIO = -0x61c88647

/**
 * Amount of encryption rounds.
 */
private const val XTEA_ROUNDS = 32

/**
 * XTEA encrypts a [ByteBuffer].
 */
@Suppress("MagicNumber")
internal fun ByteBuffer.xteaEncrypt(key: IntArray, start: Int, end: Int): ByteArray {
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
    return array()
}


/**
 * XTEA decrypts a [ByteBuffer].
 */
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

/**
 * Textbook/Plain RSA encryption/decryption.
 */
internal fun rsaCrypt(data: ByteArray, mod: BigInteger, key: BigInteger) =
    BigInteger(data).modPow(key, mod).toByteArray()

/**
 * Custom character set used in the RuneTek engine.
 */
val charset = charArrayOf('€', '\u0000', '‚', 'ƒ', '„', '…', '†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', '\u0000',
    'Ž', '\u0000', '\u0000', '‘', '’', '“', '”', '•', '–', '—', '˜', '™', 'š', '›', 'œ', '\u0000', 'ž', 'Ÿ'
)

/**
 * Rounds a value to the next power of 2.
 */
fun nextPowerOfTwo(value: Int): Int {
    var result = value
    --result
    result = result or result.ushr(1)
    result = result or result.ushr(2)
    result = result or result.ushr(4)
    result = result or result.ushr(8)
    result = result or result.ushr(16)
    return result + 1
}

/**
 * Transform a character to a character used in the RuneTek engine.
 */
fun toJagexChar(char: Int): Char = if (char in 128..159) {
    var curChar = charset[char - 128]
    if (curChar.toInt() == 0) {
        curChar = 63.toChar()
    }
    curChar
} else {
    char.toChar()
}

/**
 * Transform a character from the RuneTek engine to an encoded character.
 */
fun toEncodedChar(char: Char): Int = if(charset.contains(char)) {
    if(char.toInt() == 63) {
        128
    } else {
        charset.indexOf(char) + 128
    }
} else {
    char.toInt()
}
