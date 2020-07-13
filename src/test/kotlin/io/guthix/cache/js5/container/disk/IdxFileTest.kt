/*
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
 * along with Guthix Jagex-Store-5. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5.container.disk

import io.kotest.core.spec.autoClose
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