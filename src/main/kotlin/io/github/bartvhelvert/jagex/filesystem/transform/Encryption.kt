package io.github.bartvhelvert.jagex.filesystem.transform

import java.math.BigInteger
import java.nio.ByteBuffer

object XTEA {
    const val GOLDEN_RATIO = -0x61c88647
    const val ROUNDS = 32
    const val KEY_SIZE = 4
    val ZERO_KEY = IntArray(KEY_SIZE)
}

fun ByteBuffer.xteaEncrypt(keys: IntArray, start: Int, end: Int): ByteBuffer {
    require(keys.size == XTEA.KEY_SIZE)
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = 0
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(XTEA.ROUNDS) {
            v0 += (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + keys[sum and 3]
            sum += XTEA.GOLDEN_RATIO
            v1 += (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + keys[sum.ushr(11) and 3]
        }
        putInt(start + i * 8, v0)
        putInt(start + i * 8 + 4, v1)
    }
    return this
}

@Suppress("INTEGER_OVERFLOW")
fun ByteBuffer.xteaDecrypt(key: IntArray, start: Int = 0, end: Int = limit()): ByteBuffer {
    require(key.size == XTEA.KEY_SIZE)
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = XTEA.GOLDEN_RATIO * XTEA.ROUNDS
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(XTEA.ROUNDS) {
            v1 -= (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
            sum -= XTEA.GOLDEN_RATIO
            v0 -= (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
        }
        putInt(start + i * 8, v0)
        putInt(start + i * 8 + 4, v1)
    }
    return this
}

fun rsaCrypt(buffer: ByteBuffer, mod: BigInteger, key: BigInteger): ByteBuffer =
    ByteBuffer.wrap(BigInteger(buffer.array()).modPow(key, mod).toByteArray())

