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

import io.guthix.js5.container.Js5Container
import io.guthix.js5.container.Js5Store
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

private val logger = KotlinLogging.logger {}

/**
 * A [Js5DiskStore] used to read and write [Js5Container] data to and from disk a store on disk.
 *
 * @property root The root folder containing the cache data.
 * @property dat2File The [Dat2File] containing the cache data.
 * @property indexFiles The index files
 * @property archiveCount The amount of archives in the [Js5DiskStore].
 */
public class Js5DiskStore private constructor(
    private val root: Path,
    private val dat2File: Dat2File,
    private val indexFiles: MutableMap<Int, IdxFile>,
    override var archiveCount: Int
) : Js5Store {
    override fun read(indexId: Int, containerId: Int): ByteBuf {
        require(indexId in 0 until archiveCount || indexId == Js5Store.MASTER_INDEX) {
            "Can't read data because $FILE_NAME.${IdxFile.EXTENSION}${indexId} does not exist."
        }
        val indexFile = indexFiles.getOrPut(indexId, { openIndexFile(indexId) })
        val index = indexFile.read(containerId)
        if (index.dataSize == 0) {
            logger.warn {
                "Could not read index file ${indexFile.id} container $containerId because the index does not exist"
            }
            return Unpooled.EMPTY_BUFFER
        }
        logger.trace { "Reading index file ${indexFile.id} container $containerId" }
        return dat2File.read(indexFile.id, containerId, index)
    }

    override fun write(indexId: Int, containerId: Int, data: ByteBuf) {
        require(indexId in 0..archiveCount || indexId == Js5Store.MASTER_INDEX) {
            "Can't write data because $FILE_NAME.${IdxFile.EXTENSION}${indexId} does not exist and can't be created."
        }
        logger.trace { "Writing index file $indexId container $containerId" }
        val indexFile = if (indexId == archiveCount) {
            createNewArchive()
        } else {
            indexFiles.getOrPut(indexId, { openIndexFile(indexId) })
        }
        val overWriteIndex = indexFile.containsIndex(containerId)
        val firstSegNumber = if (overWriteIndex) {
            indexFile.read(containerId).sectorNumber
        } else {
            ceil(dat2File.size.toDouble() / Sector.SIZE).toInt() // last sector of the data file
        }
        val index = Index(data.readableBytes(), firstSegNumber)
        indexFile.write(containerId, index)
        dat2File.write(indexFile.id, containerId, index, data)
    }

    /**
     * Removes an [Index] from the [Js5DiskStore]. Note that this method does not remove the actual data but only the
     * reference to the data. It is recommended to defragment the cache after calling this method.
     */
    override fun remove(indexId: Int, containerId: Int) {
        require(indexId in 0 until archiveCount || indexId == Js5Store.MASTER_INDEX) {
            "Can't remove data because $FILE_NAME.${IdxFile.EXTENSION}${indexId} does not exist."
        }
        val indexFile = indexFiles.getOrPut(indexId, { openIndexFile(indexId) })
        logger.trace { "Removing index file ${indexFile.id} container $containerId" }
        indexFile.remove(containerId)
    }

    /**
     * Creates a new archive [IdxFile] in this [Js5DiskStore].
     */
    private fun createNewArchive(): IdxFile {
        val file = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$archiveCount")
        logger.debug { "Created index file ${file.fileName}" }
        Files.createFile(file)
        val indexFile = IdxFile.open(archiveCount, file)
        indexFiles[archiveCount++] = indexFile
        return indexFile
    }

    /**
     * Opens an [IdxFile] in this [Js5DiskStore].
     */
    private fun openIndexFile(indexFileId: Int) = IdxFile.open(
        indexFileId, root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$indexFileId")
    )

    override fun close() {
        logger.debug { "Closing Js5DiskStore at $root" }
        dat2File.close()
        indexFiles.values.forEach(IdxFile::close)
    }

    public companion object {
        /**
         * The default cache file name.
         */
        public const val FILE_NAME: String = "main_file_cache"

        /**
         * Opens a [Js5DiskStore].
         *
         * @param root The folder where the cache is located.
         */
        public fun open(root: Path): Js5DiskStore {
            require(Files.isDirectory(root)) { "$root is not a directory or doesn't exist." }
            val dataFile = Dat2File.open(root.resolve("$FILE_NAME.${Dat2File.EXTENSION}"))
            val masterIndexFile = IdxFile.open(
                Js5Store.MASTER_INDEX, root.resolve("$FILE_NAME.${IdxFile.EXTENSION}${Js5Store.MASTER_INDEX}")
            )
            var archiveCount = 0
            for (indexFileId in 0 until Js5Store.MASTER_INDEX) {
                val indexPath = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$indexFileId")
                if (!Files.exists(indexPath)) {
                    archiveCount = indexFileId
                    break
                }
            }
            logger.debug { "Opened disk store with archive count $archiveCount" }
            return Js5DiskStore(root, dataFile, mutableMapOf(Js5Store.MASTER_INDEX to masterIndexFile), archiveCount)
        }
    }
}