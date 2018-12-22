package io.github.bartvhelvert.jagex.filesystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediumTest {
    @ExperimentalUnsignedTypes
    @ParameterizedTest
    @MethodSource("mediumTestArgs")
    fun mediumPutGetTest(input: Int, expected: Int) {
        val buffer = ByteBuffer.allocate(3).putMedium(input).flip() as ByteBuffer
        val mediumNumber = buffer.medium
        assertEquals(expected, mediumNumber)
    }

    @ExperimentalUnsignedTypes
    @ParameterizedTest
    @MethodSource("unsignedMediumTestArgs")
    fun unsignedMediumPutGetTest(input: Int, expected: Int) {
        val buffer = ByteBuffer.allocate(3).putMedium(input).flip() as ByteBuffer
        val mediumNumber = buffer.medium
        assertEquals(expected, mediumNumber)
    }

    companion object {
        @JvmStatic
        fun mediumTestArgs() = listOf(
            Arguments.of(0, 0),
            Arguments.of(10, 10),
            Arguments.of(16777215, -1),
            Arguments.of(-1, -1),
            Arguments.of(-10, -10),
            Arguments.of(8388607, 8388607)
        )

        @JvmStatic
        fun unsignedMediumTestArgs() = listOf(
            Arguments.of(0, 0),
            Arguments.of(10, 10),
            Arguments.of(16777215, 16777215),
            Arguments.of(-1, 16777215),
            Arguments.of(-10, 16777206),
            Arguments.of(8388607, 8388607)
        )
    }
}
