package io.github.bartvhelvert.jagex.filesystem

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

class ArchiveTest {
    @ParameterizedTest
    @MethodSource("encodeDecodegrCTestArgs")
    @ExperimentalUnsignedTypes
    fun encodeDecodeTest(archive: Archive, groupCount: Int, containerVersion: Int) {
        Assertions.assertEquals(archive, Archive.decode(archive.encode(groupCount, containerVersion), archive.attributes))
    }

    companion object {
        @JvmStatic
        fun encodeDecodegrCTestArgs() = listOf(
            Arguments.of( // group = 1 test
                Archive(
                   ArchiveAttributes(
                       id = 1,
                       nameHash = null,
                       crc = 231231,
                       unknownHash = null,
                       whirlpoolHash = null,
                       sizes = null,
                       version = 10,
                       fileAttributes = mutableMapOf(
                           1 to FileAttributes(id = 1, nameHash = null),
                           2 to FileAttributes(id = 2, nameHash = null)
                       )
                   ),
                    arrayOf(
                        ByteBuffer.allocate(8).apply {
                            put(8)
                            put(3)
                            putShort(4)
                            putInt(8)
                        }.flip(),
                        ByteBuffer.allocate(8).apply {
                            put(20)
                            put(0)
                            putShort(24854)
                            putInt(93432)
                        }.flip()
                    )
                ),
                1, // groupCount
                1 // containerVersion
            ),
            Arguments.of( // group = 8 test
                Archive(
                    ArchiveAttributes(
                        id = 1,
                        nameHash = null,
                        crc = 231231,
                        unknownHash = null,
                        whirlpoolHash = null,
                        sizes = null,
                        version = 10,
                        fileAttributes = mutableMapOf(
                            1 to FileAttributes(id = 1, nameHash = null),
                            2 to FileAttributes(id = 2, nameHash = null)
                        )
                    ),
                    arrayOf(
                        ByteBuffer.allocate(8).apply {
                            put(8)
                            put(3)
                            putShort(4)
                            putInt(8)
                        }.flip(),
                        ByteBuffer.allocate(8).apply {
                            put(20)
                            put(0)
                            putShort(24854)
                            putInt(93432)
                        }.flip()
                    )
                ),
                8, // groupCount
                1 // containerVersion
            ),
            Arguments.of( // test = -1
                Archive(
                    ArchiveAttributes(
                        id = 1,
                        nameHash = null,
                        crc = 231231,
                        unknownHash = null,
                        whirlpoolHash = null,
                        sizes = null,
                        version = 10,
                        fileAttributes = mutableMapOf(
                            1 to FileAttributes(id = 1, nameHash = null),
                            2 to FileAttributes(id = 2, nameHash = null)
                        )
                    ),
                    arrayOf(
                        ByteBuffer.allocate(8).apply {
                            put(8)
                            put(3)
                            putShort(4)
                            putInt(8)
                        }.flip(),
                        ByteBuffer.allocate(8).apply {
                            put(20)
                            put(0)
                            putShort(24854)
                            putInt(93432)
                        }.flip()
                    )
                ),
                1, // groupCount
                -1 // containerVersion
            )
        )
    }
}