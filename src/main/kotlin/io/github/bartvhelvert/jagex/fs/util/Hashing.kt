package io.github.bartvhelvert.jagex.fs.util

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

const val whirlPoolHashByteCount = 64

fun whirlPoolHash(data: ByteArray):ByteArray {
    Security.addProvider(BouncyCastleProvider())
    return MessageDigest.getInstance("Whirlpool").digest(data)
}