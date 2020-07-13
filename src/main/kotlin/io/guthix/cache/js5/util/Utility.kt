/*
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
 * along with Guthix Jagex-Store-5. If not, see <https://www.gnu.org/licenses/>.
 */
@file:Suppress("DuplicatedCode")

package io.guthix.cache.js5.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Security
import java.util.zip.CRC32

private val crc = CRC32()

/**
 * Calculates the [java.util.zip.CRC32] of a [ByteArray].
 */
public fun ByteBuf.crc(index: Int = readerIndex(), length: Int = readableBytes()): Int {
    crc.reset()
    if (hasArray()) {
        val start = if (this is CompositeByteBuf) component(0).readerIndex() else index
        crc.update(array(), start, length)
    } else {
        val copyArray = ByteArray(length)
        getBytes(index, copyArray)
        crc.update(copyArray, 0, length)
    }
    return crc.value.toInt()
}

/**
 * The amount of bytes in a whirlpool hash.
 */
internal const val WHIRLPOOL_HASH_SIZE = 64

/**
 * The whirlpool digest.
 */
private val wpDigest: MessageDigest by lazy {
    Security.addProvider(BouncyCastleProvider())
    MessageDigest.getInstance("Whirlpool")
}

/**
 * Calculates the whirlpool hash for a [ByteBuf].
 */
public fun ByteBuf.whirlPoolHash(index: Int = readerIndex(), length: Int = readableBytes()): ByteArray {
    Security.addProvider(BouncyCastleProvider())
    if (hasArray()) {
        val start = if (this is CompositeByteBuf) component(0).readerIndex() else index
        wpDigest.update(array(), start, length)
    } else {
        val copyArray = ByteArray(length)
        getBytes(index, copyArray)
        wpDigest.update(copyArray, 0, length)
    }
    return wpDigest.digest()
}

/**
 * Textbook/Plain RSA encryption/decryption.
 */
internal fun rsaCrypt(data: ByteArray, mod: BigInteger, key: BigInteger) =
    BigInteger(data).modPow(key, mod).toByteArray()
