package io.github.bartvhelvert.jagex.fs.io


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