package io.guthix.cache.fs.io.bytebufferextensions

import io.guthix.cache.fs.io.putMedium
import io.guthix.cache.fs.io.skip
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkipTest {
    @ParameterizedTest
    @MethodSource("writeAfterSkipArgs")
    fun writeAfterSkipTest(amount: Int, value: Byte, writable: ByteBuffer, expected: ByteBuffer) {
        writable.skip(amount).put(value)
        Assertions.assertEquals(expected, writable)
    }

    companion object {
        @JvmStatic
        fun writeAfterSkipArgs(): List<Arguments> {
            val testValue = 93.toByte()
            return listOf(
                Arguments.of(3, testValue,
                    ByteBuffer.allocate(6).apply {
                        put(3)
                        put(5)
                    },
                    ByteBuffer.allocate(6).apply {
                        put(3)
                        put(5)
                        putMedium(0) // 3 bytes
                        put(testValue)
                    }
                ),
                Arguments.of(0, testValue,
                    ByteBuffer.allocate(3).apply {
                        put(3)
                        put(5)
                    },
                    ByteBuffer.allocate(3).apply {
                        put(3)
                        put(5)
                        put(testValue)
                    }
                ),
                Arguments.of(-1, 94.toByte(),
                    ByteBuffer.allocate(2).apply {
                        put(3)
                        put(5)
                    },
                    ByteBuffer.allocate(2).apply {
                        put(3)
                        put(testValue)
                    }
                )
            )
        }
    }
}