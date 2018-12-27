package io.github.bartvhelvert.jagex.filesystem.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SegmentTest {
    @ParameterizedTest
    @MethodSource("encodeDecodeNormalTestArgs")
    @ExperimentalUnsignedTypes
    internal fun encodeDecodeNormalTest(segment: Segment)=
        assertEquals(segment, Segment.decode(segment.encode()))

    @ParameterizedTest
    @MethodSource("encodeDecodeExtendedTestArgs")
    @ExperimentalUnsignedTypes
    internal fun encodeDecodeExtendedTest(segment: Segment)=
        assertEquals(segment, Segment.decodeExtended(segment.encode()))

    companion object {
        @JvmStatic
        @ExperimentalUnsignedTypes
        fun encodeDecodeNormalTestArgs() = listOf(
            Arguments.of(Segment(
                indexId = 1.toUByte(),
                containerId = 1 ,
                segmentPos = 1.toUShort(),
                nextSegmentPos = 2,
                data = ByteArray(Segment.DATA_SIZE))
            )
        )

        @JvmStatic
        @ExperimentalUnsignedTypes
        fun encodeDecodeExtendedTestArgs() = listOf(
            Arguments.of(Segment(
                indexId = 1.toUByte(),
                containerId = UShort.MAX_VALUE.toInt() + 1,
                segmentPos = 1.toUShort(),
                nextSegmentPos = 2,
                data = ByteArray(Segment.EXTENDED_DATA_SIZE))
            )
        )
    }
}