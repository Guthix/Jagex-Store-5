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
package io.guthix.cache.js5

import io.guthix.cache.js5.util.WP_HASH_BYTE_COUNT
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Js5CacheChecksumTest {
    @ParameterizedTest
    @MethodSource("testChecksumsNoWhirlpool", "testChecksumsWhirlpool")
    fun `Encode and decode compare checksum`(
        whirlpool: Boolean,
        js5CacheCheckSum: Js5CacheChecksum,
        mod: BigInteger?,
        pubKey: BigInteger?,
        privateKey: BigInteger?
    ) {
        Assertions.assertEquals(js5CacheCheckSum,
            Js5CacheChecksum.decode(
                js5CacheCheckSum.encode(whirlpool, mod, pubKey),
                whirlpool,
                mod,
                privateKey
            )
        )
    }

    companion object {
        @JvmStatic
        fun testChecksumsNoWhirlpool() = listOf(
            Arguments.of(
                true,
                Js5CacheChecksum(
                    arrayOf(
                        ArchiveChecksum(
                            crc = 87585,
                            version = 1,
                            fileCount = 0,
                            size = 12,
                            whirlpoolDigest = ByteArray(WP_HASH_BYTE_COUNT)
                        ),
                        ArchiveChecksum(
                            crc = 3331,
                            version = 3,
                            fileCount = 3,
                            size = 12,
                            whirlpoolDigest = ByteArray(WP_HASH_BYTE_COUNT)
                        )
                    )
                ),
                null, null, null
            )
        )

        @JvmStatic
        fun testChecksumsWhirlpool() = listOf(
            Arguments.of(
                false,
                Js5CacheChecksum(
                    arrayOf(
                        ArchiveChecksum(
                            crc = 87585,
                            version = 1,
                            fileCount = 0,
                            size = 0,
                            whirlpoolDigest = null
                        ),
                        ArchiveChecksum(
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
                Js5CacheChecksum(
                    arrayOf(
                        ArchiveChecksum(
                            crc = 87585,
                            version = 1,
                            fileCount = 0,
                            size = 0,
                            whirlpoolDigest = null
                        ),
                        ArchiveChecksum(
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