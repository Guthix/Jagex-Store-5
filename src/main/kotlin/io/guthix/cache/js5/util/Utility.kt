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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Security
import java.util.zip.CRC32

private val crc = CRC32()

/**
 * Calculates the [java.util.zip.CRC32] of a [ByteArray].
 */
fun ByteBuf.crc(index: Int = readerIndex(), length: Int = readableBytes()): Int {
    crc.reset()
    crc.update(this.array(), index, length)
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
fun ByteBuf.whirlPoolHash(index: Int = readerIndex(), length: Int = readableBytes()): ByteArray {
    Security.addProvider(BouncyCastleProvider())
    wpDigest.update(this.array(), index, length)
    return wpDigest.digest()
}

/**
 * Textbook/Plain RSA encryption/decryption.
 */
internal fun rsaCrypt(data: ByteArray, mod: BigInteger, key: BigInteger) =
    BigInteger(data).modPow(key, mod).toByteArray()
