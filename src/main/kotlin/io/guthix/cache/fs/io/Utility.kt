/*
Copyright (C) 2019 Guthix

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.fs.io

val charset = charArrayOf('€', '\u0000', '‚', 'ƒ', '„', '…', '†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', '\u0000',
    'Ž', '\u0000', '\u0000', '‘', '’', '“', '”', '•', '–', '—', '˜', '™', 'š', '›', 'œ', '\u0000', 'ž', 'Ÿ'
)

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

fun toJagexChar(char: Int): Char = if (char in 128..159) {
    var curChar = charset[char - 128]
    if (curChar.toInt() == 0) {
        curChar = 63.toChar()
    }
    curChar
} else {
    char.toChar()
}

fun toEncodedChar(char: Char): Int = if(charset.contains(char)) {
    if(char.toInt() == 63) {
        128
    } else {
        charset.indexOf(char) + 128
    }
} else {
    char.toInt()
}