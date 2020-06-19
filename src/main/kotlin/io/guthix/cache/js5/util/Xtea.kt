/**
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
/**
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

/**
 * The amount of [Int] keys in a XTEA key.
 */
internal const val XTEA_KEY_SIZE = 4

/**
 * A 0 XTEA key.
 */
internal val XTEA_ZERO_KEY = IntArray(XTEA_KEY_SIZE)

/**
 * The XTEA golden ratio.
 */
private const val GOLDEN_RATIO = -0x61c88647

/**
 * Amount of encryption rounds.
 */
private const val ROUNDS = 32

private const val QUAD_ENCODED_SIZE = Int.SIZE_BYTES + Int.SIZE_BYTES

/**
 * Encrypts a [ByteBuf] using XTEA encryption in place.
 */
@Suppress("MagicNumber")
internal fun ByteBuf.xteaEncrypt(key: IntArray, start: Int = readerIndex(), end: Int = writerIndex()): ByteBuf {
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
 * Decrypts a [ByteBuf] using XTEA encryption and stores the result in a new buffer.
 */
@Suppress("INTEGER_OVERFLOW")
internal fun ByteBuf.xteaDecrypt(key: IntArray, start: Int = readerIndex(), end: Int = writerIndex()): ByteBuf {
    require(key.size == XTEA_KEY_SIZE) { "The XTEA key should be 128 byte long." }
    val result = Unpooled.buffer(readableBytes())
    for (i in 0 until (end - start) / QUAD_ENCODED_SIZE) {
        var sum = GOLDEN_RATIO * ROUNDS
        var v0 = readInt()
        var v1 = readInt()
        repeat(ROUNDS) {
            v1 -= (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
            sum -= GOLDEN_RATIO
            v0 -= (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
        }
        result.writeInt(v0)
        result.writeInt(v1)
    }
    return result.writeBytes(this, readableBytes())
}