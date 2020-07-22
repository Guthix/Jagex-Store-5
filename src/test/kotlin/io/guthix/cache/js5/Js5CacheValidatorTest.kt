/*
 * Copyright 2018-2020 Guthix
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.guthix.cache.js5

import io.kotest.core.spec.style.StringSpec

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
        Js5CacheValidator.decode(checksum.encode(), whirlpoolIncluded = false, sizeIncluded = false)
    }
})