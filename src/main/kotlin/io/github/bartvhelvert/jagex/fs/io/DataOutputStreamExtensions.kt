package io.github.bartvhelvert.jagex.fs.io

import java.io.DataOutputStream

fun DataOutputStream.writeSmart(value: Int) {
    if(value <= Short.MAX_VALUE) {
        writeShort(value)
    } else {
        writeInt(value)
    }
}

fun DataOutputStream.writeString(string: String) {
    string.forEach { char ->
        if(charset.contains(char)) {
            if(char.toInt() == 63) {
                writeByte(128)
            } else {
                charset.indexOf(char) + 128
            }
        } else {
            writeByte(char.toInt())
        }
    }
    writeByte(0)
}