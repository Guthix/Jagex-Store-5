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
package io.guthix.cache.js5

import io.kotest.core.spec.style.StringSpec
import io.netty.buffer.Unpooled

class Js5GroupDataTest : StringSpec({
    val groupDataSing = Js5GroupData(arrayOf(Unpooled.buffer(5893).iterationFill()))
    "After encoding and decoding the group data with a single file it should be the same as the original" {
        Js5GroupData.decode(groupDataSing.encode(), groupDataSing.fileData.size)
    }

    val groupDataMult = Js5GroupData(arrayOf(
        Unpooled.buffer(231).iterationFill(),
        Unpooled.buffer(231).iterationFill(),
        Unpooled.buffer(123).iterationFill()
    ))
    "After encoding and decoding the group data with multiple files it should be the same as the original" {
        Js5GroupData.decode(groupDataMult.encode(), groupDataMult.fileData.size)
    }

    "After encoding and decoding the group data with multiple files and chunks it should be the same as the original" {
        groupDataMult.chunkCount = 10
        Js5GroupData.decode(groupDataMult.encode(), groupDataMult.fileData.size)
    }
})