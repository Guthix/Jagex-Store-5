/*
 * Copyright (C) 2019 Guthix
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.fs

import io.guthix.cache.fs.util.whirlPoolHashByteCount
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger

class CacheChecksumTest {
    @ParameterizedTest
    @MethodSource("encodeDecodeNoWPTestArgs", "encodeDecodeWPTestArgs")
    @ExperimentalUnsignedTypes
    fun encodeDecodeTest(
        whirlpool: Boolean,
        cacheCheckSum: CacheChecksum,
        mod: BigInteger?,
        pubKey: BigInteger?,
        privateKey: BigInteger?
    ) {
        Assertions.assertEquals(cacheCheckSum,
            CacheChecksum.decode(
                cacheCheckSum.encode(whirlpool, mod, pubKey),
                whirlpool,
                mod,
                privateKey
            )
        )
    }

    companion object {
        @JvmStatic
        fun encodeDecodeNoWPTestArgs() = listOf(
            Arguments.of(
                true,
                CacheChecksum(
                    arrayOf(
                        DictionaryChecksum(
                            crc = 87585,
                            version = 1,
                            fileCount = 0,
                            size = 12,
                            whirlpoolDigest = ByteArray(whirlPoolHashByteCount)
                        ),
                        DictionaryChecksum(
                            crc = 3331,
                            version = 3,
                            fileCount = 3,
                            size = 12,
                            whirlpoolDigest = ByteArray(whirlPoolHashByteCount)
                        )
                    )
                ),
                null, null, null
            )
        )

        @JvmStatic
        fun encodeDecodeWPTestArgs() = listOf(
            Arguments.of(
                false,
                CacheChecksum(
                    arrayOf(
                        DictionaryChecksum(
                            crc = 87585,
                            version = 1,
                            fileCount = 0,
                            size = 0,
                            whirlpoolDigest = null
                        ),
                        DictionaryChecksum(
                            crc = 3331,
                            version = 3,
                            fileCount = 0,
                            size = 0,
                            whirlpoolDigest = null
                        )
                    )
                ),
                BigInteger.valueOf(3233), // mod
                BigInteger.valueOf(17), // pub key
                BigInteger.valueOf(413) // private key
            ),
            Arguments.of(
                false,
                CacheChecksum(
                    arrayOf(
                        DictionaryChecksum(
                            crc = 87585,
                            version = 1,
                            fileCount = 0,
                            size = 0,
                            whirlpoolDigest = null
                        ),
                        DictionaryChecksum(
                            crc = 3331,
                            version = 3,
                            fileCount = 0,
                            size = 0,
                            whirlpoolDigest = null
                        )
                    )
                ),
                null, null, null
            )
        )
    }
}