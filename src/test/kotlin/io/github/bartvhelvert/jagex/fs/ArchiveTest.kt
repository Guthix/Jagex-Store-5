package io.github.bartvhelvert.jagex.fs

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.ByteBuffer

class ArchiveTest {
    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    fun encodeDecodeTest(archive: Archive, groupCount: Int, containerVersion: Int) {
        val fileAttributes = mutableMapOf<Int, FileAttributes>()
        archive.files.forEach { fileId, file ->
            fileAttributes[fileId] = FileAttributes(fileId, file.nameHash)
        }
        Assertions.assertEquals(archive,
            Archive.decode(
                archive.encode(groupCount, containerVersion),
                ArchiveAttributes(
                    archive.id,
                    archive.nameHash,
                    archive.crc,
                    archive.unknownHash,
                    archive.whirlpoolHash,
                    archive.sizes,
                    archive.version,
                    fileAttributes
                )
            )
        )
    }

    companion object {
        @JvmStatic
        fun encodeDecodeTestArgs(): List<Arguments> {
            val buffer1 = ByteBuffer.allocate(8).apply {
                put(8)
                put(3)
                putShort(4)
                putInt(8)
            }.flip()
            val buffer2 = ByteBuffer.allocate(8).apply {
                put(20)
                put(0)
                putShort(24854)
                putInt(93432)
            }.flip()
            val encodeData = mapOf(
                1 to Archive.File(1, buffer1, null),
                2 to Archive.File(2, buffer2, null)
            )
            return listOf(
                Arguments.of( // group = 1 test
                    Archive(
                        id = 1,
                        nameHash = null,
                        crc = 231231,
                        unknownHash = null,
                        whirlpoolHash = null,
                        sizes = null,
                        version = 10,
                        files = encodeData
                    ),
                    1, // groupCount
                    1 // containerVersion
                ),
                Arguments.of( // group = 8 test
                    Archive(
                        id = 1,
                        nameHash = null,
                        crc = 231231,
                        unknownHash = null,
                        whirlpoolHash = null,
                        sizes = null,
                        version = 10,
                        files = encodeData
                    ),
                    8, // groupCount
                    1 // containerVersion
                ),
                Arguments.of( // version = -1 test
                    Archive(
                        id = 1,
                        nameHash = null,
                        crc = 231231,
                        unknownHash = null,
                        whirlpoolHash = null,
                        sizes = null,
                        version = 10,
                        files = encodeData
                    ),
                    1, // groupCount
                    -1 // containerVersion
                )
            )
        }
    }
}