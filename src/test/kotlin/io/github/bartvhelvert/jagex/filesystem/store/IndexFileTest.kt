package io.github.bartvhelvert.jagex.filesystem.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class IndexTest {
    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    internal fun encodeDecodeTest(index: Index) =
            assertEquals(index, Index.decode(index.encode()))

    companion object {
        @JvmStatic
        @ExperimentalUnsignedTypes
        fun encodeDecodeTestArgs() = listOf(
            Arguments.of(Index(dataSize = 10, segmentPos = 1)),
            Arguments.of(Index(dataSize = 3, segmentPos = 20))
        )
    }
}