package io.github.bartvhelvert.jagex.fs.io

import java.io.DataOutputStream

fun DataOutputStream.writeSmart(value: Int) {
    if(value <= Short.MAX_VALUE) {
        writeShort(value)
    } else {
        writeInt(value)
    }
}