package io.github.bartvhelvert.jagex.fs

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class AttributesTest {
    @ParameterizedTest
    @MethodSource("encodeDecodeTestArgs")
    @ExperimentalUnsignedTypes
    fun encodeDecodeTest(dictAttr: DictionaryAttributes, containerVersion: Int, format: DictionaryAttributes.Format) {
        Assertions.assertEquals(
            dictAttr,
            DictionaryAttributes.decode(
                Container(containerVersion, dictAttr.encode(format))
            )
        )
    }

    companion object {
        @JvmStatic
        fun encodeDecodeTestArgs() = listOf(
            Arguments.of(
                DictionaryAttributes(
                    version = 10,
                    archiveAttributes = mutableMapOf(
                        1 to ArchiveAttributes(
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
                        2 to ArchiveAttributes(
                            id = 2,
                            nameHash = null,
                            crc = 58529,
                            unknownHash = null,
                            whirlpoolHash = null,
                            sizes = null,
                            version = 3,
                            fileAttributes = mutableMapOf(
                                1 to FileAttributes(id = 1, nameHash = null),
                                2 to FileAttributes(id = 2, nameHash = null)
                            )
                        )
                    )
                ),
                3,
                DictionaryAttributes.Format.VERSIONED
            )
        )
    }
}