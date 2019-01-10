/*
   Copyright 2018 Bart van Helvert

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package io.github.bartvhelvert.jagex.fs.io

import java.nio.ByteBuffer

@ExperimentalUnsignedTypes
fun ByteBuffer.getUByte(pos: Int) = get(pos).toUByte()

@ExperimentalUnsignedTypes
val ByteBuffer.uByte get() = get().toUByte()

@ExperimentalUnsignedTypes
val ByteBuffer.uShort get() = short.toUShort()

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

@ExperimentalUnsignedTypes
val ByteBuffer.smart get() = if (get(position()) < 0) {
    uInt
} else {
    uShort.toInt()
}

val charset = charArrayOf('€', '\u0000', '‚', 'ƒ', '„', '…', '†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', '\u0000',
    'Ž', '\u0000', '\u0000', '‘', '’', '“', '”', '•', '–', '—', '˜', '™', 'š', '›', 'œ', '\u0000', 'ž', 'Ÿ')

@ExperimentalUnsignedTypes
val ByteBuffer.string get(): String {
    val bldr = StringBuilder()
    var encodedByte: Int = uByte.toInt()
    while (encodedByte != 0) {
        if (encodedByte in 128..159) {
            var curChar = charset[encodedByte - 128]
            if (curChar.toInt() == 0) {
                curChar = 63.toChar()
            }
            bldr.append(curChar)
        } else {
            bldr.append(encodedByte.toChar())
        }
        encodedByte = get().toInt()
    }
    return bldr.toString()
}

fun ByteBuffer.splitOf(index: Int, splits: Int): ByteBuffer {
    val start = Math.ceil(limit().toDouble() / splits.toDouble()).toInt() * (index - 1)
    var end = Math.ceil(limit().toDouble() / splits.toDouble()).toInt() * index
    if(end > limit()) end = limit()
    return ByteBuffer.wrap(array().sliceArray(start until end))
}