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
package io.guthix.js5

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