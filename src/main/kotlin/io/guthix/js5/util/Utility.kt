/*
 * Copyright 2018-2020 Guthix
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
@file:Suppress("DuplicatedCode")

package io.guthix.js5.util

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
