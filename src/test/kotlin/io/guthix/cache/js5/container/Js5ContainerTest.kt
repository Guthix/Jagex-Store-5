/**
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
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