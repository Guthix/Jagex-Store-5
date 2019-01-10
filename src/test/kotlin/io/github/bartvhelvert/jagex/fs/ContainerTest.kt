package io.github.bartvhelvert.jagex.fs

import io.github.bartvhelvert.jagex.fs.util.Compression
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

class ContainerTest {
    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    internal fun compNoneEncodeDecodeTest(container: Container) =
        Assertions.assertEquals(container, Container.decode(container.encode(Compression.NONE)))

    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    internal fun compGZIPEncodeDecodeTest(container: Container) =
        Assertions.assertEquals(container, Container.decode(container.encode(Compression.GZIP)))

    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    internal fun compBZIP2EncodeDecodeTest(container: Container) =
        Assertions.assertEquals(container, Container.decode(container.encode(Compression.BZIP2)))

    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    internal fun encNoneEncodeDecodeTest(container: Container) {
        val xteaKey = intArrayOf(376495908, 4927, 37654959, 936549)
        Assertions.assertEquals(container, Container.decode(container.encode(Compression.NONE, xteaKey), xteaKey))
    }

    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    internal fun encGZIPEncodeDecodeTest(container: Container) {
        val xteaKey = intArrayOf(376495908, 4927, 37654959, 936549)
        Assertions.assertEquals(container, Container.decode(container.encode(Compression.GZIP, xteaKey), xteaKey))
    }

    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    internal fun encBZIP2EncodeDecodeTest(container: Container) {
        val xteaKey = intArrayOf(376495908, 4927, 37654959, 936549)
        Assertions.assertEquals(container, Container.decode(container.encode(Compression.BZIP2, xteaKey), xteaKey))
    }

    companion object {
        @JvmStatic
        @ExperimentalUnsignedTypes
        fun encodeDecodeTestArgs() = listOf(
            Arguments.of(Container(-1, ByteBuffer.allocate(8).apply {
                put(8)
                put(3)
                putShort(4)
                putInt(8)
            }.flip())),
            Arguments.of(Container(10, ByteBuffer.allocate(8).apply {
                put(-1)
                put(10)
                putShort(30)
                putInt(900)
            }.flip()))
        )
    }
}