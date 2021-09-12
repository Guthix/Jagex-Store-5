/*
 * Copyright 2018-2021 Guthix
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.guthix.js5.container

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.lang.Exception

internal class DecryptionFailedException(message: String) : Exception(message)

/** The amount of [Int] keys in a XTEA key. */
public const val XTEA_KEY_SIZE: Int = 4

/** The XTEA 0 key, no encryption is done when encrypting with a 0 key. */
public val XTEA_ZERO_KEY: IntArray = IntArray(XTEA_KEY_SIZE)

private const val GOLDEN_RATIO = -0x61c88647

private const val ROUNDS = 32

private const val QUAD_ENCODED_SIZE = Int.SIZE_BYTES + Int.SIZE_BYTES

/**
 * Encrypts a [ByteBuf] using XTEA encryption in place.
 */
@Suppress("MagicNumber")
public fun ByteBuf.xteaEncrypt(key: IntArray, start: Int = readerIndex(), end: Int = writerIndex()): ByteBuf {
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
public fun ByteBuf.xteaDecrypt(key: IntArray, start: Int = readerIndex(), end: Int = writerIndex()): ByteBuf {
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