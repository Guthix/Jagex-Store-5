/*
 * Copyright 2018-2021 Guthix
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
package io.guthix.js5.container.disk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class IdxFileTest : StringSpec() {
    init {
        val indexFile = autoClose(IdxFile.open(1,
            Files.createTempFile("main_file_cache", ".idx1"))
        )
        val index1 = Index(30587, 0)
        val containerId1 = 1
        "After writing and reading the index should be the same as the original" {
            indexFile.write(containerId1, index1)
            indexFile.read(containerId1) shouldBe index1
        }

        val index2 = Index(30587, 0)
        val containerId2 = 1
        "After writing and reading a second index the data should be the same as the original" {
            indexFile.write(containerId2, index2)
            indexFile.read(containerId2) shouldBe index2
        }
    }
}