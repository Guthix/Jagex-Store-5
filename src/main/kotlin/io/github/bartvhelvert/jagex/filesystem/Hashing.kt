package io.github.bartvhelvert.jagex.filesystem

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.Security

fun whirlPoolHash(data: ByteArray):ByteArray {
    Security.addProvider(BouncyCastleProvider())
    return MessageDigest.getInstance("Whirlpool").digest(data)
}