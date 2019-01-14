package io.github.bartvhelvert.jagex.fs.io

import java.io.DataOutputStream
import java.io.IOException

fun DataOutputStream.writeMedium(value: Int): DataOutputStream {
    require(value <= 16777215)
    writeShort((value shr 8))
    writeByte(value)
    return this
}

fun DataOutputStream.writeSmart(value: Int): DataOutputStream {
    if(value <= Short.MAX_VALUE) {
        writeShort(value)
    } else {
        writeInt(value)
    }
    return this
}

fun DataOutputStream.writeNullableSmart(value: Int?): DataOutputStream {
    when {
        value == Short.MAX_VALUE.toInt() || value == null -> writeShort(-1)
        value < Short.MAX_VALUE -> writeShort(value)
        else -> writeInt(value)
    }
    return this
}

fun DataOutputStream.writeString(string: String): DataOutputStream {
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
    return this
}

fun DataOutputStream.writeParams(params: HashMap<Int, Any>): DataOutputStream {
    writeByte(params.size)
    params.forEach { key, value ->
        if(value is String) writeByte(1) else writeByte(0)
        writeMedium(key)
        when (value) {
            is String -> writeString(value)
            is Int -> writeInt(value)
            else -> throw IOException("Unsupported param value type")
        }
    }
    return this
}