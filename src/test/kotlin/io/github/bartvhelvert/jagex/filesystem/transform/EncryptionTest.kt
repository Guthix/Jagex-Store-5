package io.github.bartvhelvert.jagex.filesystem.transform

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

class XTEATest {
    @ParameterizedTest
    @MethodSource("encryptDecryptTestArgs")
    fun encryptDecryptTest(data: ByteBuffer, keySet: IntArray) {
        val encrypted = data.xteaEncrypt(keySet,0, data.limit())
        val decrypted = encrypted.xteaDecrypt(keySet, 0, data.limit())
        Assertions.assertEquals(data, decrypted)
    }

    companion object {
        @JvmStatic
        fun encryptDecryptTestArgs()  = listOf(
            Arguments.of(
                ByteBuffer.allocate(8).apply {
                    put(8)
                    put(3)
                    putShort(4)
                    putInt(8)
                }.flip(),
                intArrayOf(586096, 984665, 1856, 578569)
            ),
            Arguments.of(
                ByteBuffer.allocate(12).apply {
                    put(32)
                    put(56)
                    putShort(5876)
                    putInt(95031)
                    putInt(3294765)
                }.flip(),
                intArrayOf(0, 0, 0, 0)
            )
        )
    }
}