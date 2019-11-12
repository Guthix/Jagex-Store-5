/**
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5

import io.guthix.cache.js5.util.WHIRLPOOL_HASH_SIZE
import io.kotlintest.specs.StringSpec

class Js5CacheValidatorTest : StringSpec({
    val checksum = Js5CacheValidator(arrayOf(
        Js5ArchiveValidator(
            crc = 32493,
            version = 3893,
            fileCount = 10,
            uncompressedSize = 39,
            whirlpoolDigest = null
        ),
        Js5ArchiveValidator(
            crc = 642,
            version = 34,
            fileCount = 1,
            uncompressedSize = 214,
            whirlpoolDigest = null
        )
    ))
    "After encoding and decoding the checksum should be the same as the original" {
        Js5CacheValidator.decode(checksum.encode())
    }

    checksum.archiveValidators.forEach { it.whirlpoolDigest = ByteArray(WHIRLPOOL_HASH_SIZE) }
    "After encoding and decoding the checksum with whirlpool it should be the same as the original" {
        Js5CacheValidator.decode(checksum.encode(), whirlpool = true)
    }
})