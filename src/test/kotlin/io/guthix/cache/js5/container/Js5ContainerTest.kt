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
package io.guthix.cache.js5.container

import io.guthix.cache.js5.iterationFill
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.netty.buffer.Unpooled

class Js5ContainerTest : StringSpec({
    val data = Unpooled.buffer(5871).iterationFill()
    "After encoding and decoding an uncompressed container it should be the same as the original" {
        Js5Container.decode(Js5Container(data.copy(), compression = Uncompressed()).encode()).data shouldBe data
    }

    "After encoding and decoding a BZIP2 compressed container it should be the same as the original" {
        Js5Container.decode(Js5Container(data.copy(), compression = BZIP2()).encode()).data shouldBe data
    }

    "After encoding and decoding a GZIP compressed container it should be the same as the original" {
        Js5Container.decode(Js5Container(data.copy(), compression = GZIP()).encode()).data shouldBe data
    }

    "After encoding and decoding a LZMA compressed container it should be the same as the original" {
        val lzma = LZMA().apply {
            header = Unpooled.buffer().apply { // write LZMA header
                writeByte(93)
                writeByte(0)
                writeByte(0)
                writeByte(64)
                writeByte(0)
            }
        }
        Js5Container.decode(Js5Container(data.copy(), compression = lzma).encode()).data shouldBe data
    }
    val xteaKey = intArrayOf(3028, 927, 0, 658)
    "After encoding and decoding a XTEA encrypted container it should be the same as the original" {
        Js5Container.decode(Js5Container(data.copy(), xteaKey = xteaKey).encode(), xteaKey = xteaKey).data shouldBe data
    }

    "After encoding and decoding a XTEA encrypted container the version should be the same as the original" {
        val version = 309
        Js5Container.decode(
            Js5Container(data.copy(), xteaKey = xteaKey, version = version).encode()
        ).version shouldBe version
    }
})