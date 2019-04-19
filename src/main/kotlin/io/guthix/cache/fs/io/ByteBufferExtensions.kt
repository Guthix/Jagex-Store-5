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
package io.guthix.cache.fs.io

import io.guthix.cache.fs.util.nextPowerOfTwo
import io.guthix.cache.fs.util.toJagexChar
import java.nio.ByteBuffer

fun ByteBuffer.skip(amount: Int): ByteBuffer = position(position() + amount)

@ExperimentalUnsignedTypes
fun ByteBuffer.peak() = get(position())

@ExperimentalUnsignedTypes
fun ByteBuffer.uPeak() = getUByte(position())

@ExperimentalUnsignedTypes
fun ByteBuffer.getUByte(pos: Int) = get(pos).toUByte()

@ExperimentalUnsignedTypes
val ByteBuffer.uByte get() = get().toUByte()

@ExperimentalUnsignedTypes
val ByteBuffer.uShort get() = short.toUShort()

@ExperimentalUnsignedTypes
fun ByteBuffer.getUShort(pos: Int) = getShort(pos).toUShort()

@ExperimentalUnsignedTypes
val ByteBuffer.uMedium get() = (short.toUShort().toInt() shl 8) or get().toUByte().toInt()

@ExperimentalUnsignedTypes
val ByteBuffer.medium get() = (short.toInt() shl 8) or get().toUByte().toInt()

fun ByteBuffer.putMedium(value: Int): ByteBuffer {
    require(value <= 16777215)
    putShort((value shr 8).toShort())
    put(value.toByte())
    return this
}

@ExperimentalUnsignedTypes
val ByteBuffer.uInt get() = int and Int.MAX_VALUE

val ByteBuffer.varInt get(): Int {
    var result = 0
    var size = get().toInt()
    while (size < 0) {
        result = result or (size and Byte.MAX_VALUE.toInt()) shl 7
        size = get().toInt()
    }
    return result or size
}

fun ByteBuffer.putVarInt(value: Int): ByteBuffer {
    if (value and -0x80 != 0) {
        if (value and -0x4000 != 0) {
            if (value and -0x200000 != 0) {
                if (value and -0x10000000 != 0) {
                    put((value.ushr(28) or 0x80).toByte())
                }
                put((value.ushr(21) or 0x80).toByte())
            }
            put((value.ushr(14) or 0x80).toByte())
        }
        put((value.ushr(7) or 0x80).toByte())
    }
    put((value and 0x7F).toByte())
    return this
}

@ExperimentalUnsignedTypes
val ByteBuffer.smallSmart get() = if(uPeak().toInt() < 128) {
    (uByte.toInt() - 64).toUShort()
} else {
    (uShort.toInt() - 49152).toUShort()
}

@ExperimentalUnsignedTypes
val ByteBuffer.smallUSmart get() = if(uPeak().toInt() < 128) {
    uByte.toUShort()
} else {
    (uShort.toInt()- 32768).toUShort()
}

@ExperimentalUnsignedTypes
val ByteBuffer.largeSmart get() = if (peak() < 0) {
    uInt
} else {
    uShort.toInt()
}

@ExperimentalUnsignedTypes
val ByteBuffer.nullableLargeSmart get() = if (peak() < 0) {
    uInt
} else {
    val temp = uShort.toInt()
    if(temp == Short.MAX_VALUE.toInt()) null else temp
}

@ExperimentalUnsignedTypes
val ByteBuffer.string get(): String {
    val bldr = StringBuilder()
    var encodedByte: Int = uByte.toInt()
    while (encodedByte != 0) {
        bldr.append(toJagexChar(encodedByte))
        encodedByte = get().toInt()
    }
    return bldr.toString()
}

@ExperimentalUnsignedTypes
val ByteBuffer.nullableString get(): String? = if(peak().toInt() != 0) string else { get(); null }

@ExperimentalUnsignedTypes
val ByteBuffer.params get(): HashMap<Int, Any> {
    val amount = uByte.toInt()
    val params = HashMap<Int, Any>(nextPowerOfTwo(amount))
    for(i in 0 until amount) {
        val isString = uByte.toInt() == 1
        params[uMedium] = if(isString) string else int
    }
    return params
}

fun ByteBuffer.splitOf(index: Int, splits: Int): ByteBuffer {
    val start = Math.ceil(limit().toDouble() / splits.toDouble()).toInt() * (index - 1)
    var end = Math.ceil(limit().toDouble() / splits.toDouble()).toInt() * index
    if(end > limit()) end = limit()
    return ByteBuffer.wrap(array().sliceArray(start until end))
}
