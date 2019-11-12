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
package io.guthix.cache.js5.container.disk

import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.Js5Archive
import io.guthix.cache.js5.Js5ArchiveSettings
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

private val logger = KotlinLogging.logger {}

/**
 * A [Js5DiskStore] used to read and write [Js5Container] data. A [Js5DiskStore] contains at least 1 [Dat2File] and 1
 * [IdxFile] called the [masterIdxFile]. The [Js5DiskStore] also contains multiple [IdxFile]s each containing [Index]es
 * to a different [Js5Archive]. All the data of the [Js5DiskStore] is stored in the [Dat2File] the [IdxFile]s contain
 * [Index]es which act like pointers to data in the [Dat2File]. The [masterIdxFile] contains [Index]es pointing to
 * meta-data for all the [Js5Archive]s stored as [Js5ArchiveSettings]. When creating a [Js5DiskStore] the [dat2File] and
 * the [masterIdxFile] are always opened because they are required for every cache operation.
 *
 * @property root The root folder containing the cache data.
 * @property dat2File The [Dat2File] containing the cache data.
 * @property masterIdxFile The master index file containing [Index]es to meta-data.
 * @property archiveCount The amount of archives in the [Js5DiskStore].
 */
class Js5DiskStore private constructor(
    private val root: Path,
    private val dat2File: Dat2File,
    val masterIdxFile: IdxFile,
    var archiveCount: Int
) : AutoCloseable {
    /**
     * Creates a new archive [IdxFile] in this [Js5DiskStore].
     */
    fun createArchiveIdxFile(): IdxFile {
        val indexFile = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$archiveCount")
        logger.debug { "Created index file ${indexFile.fileName}" }
        Files.createFile(indexFile)
        return IdxFile.open(archiveCount++, indexFile)
    }

    /**
     * Opens an [IdxFile] in this [Js5DiskStore].
     */
    fun openArchiveIdxFile(indexFileId: Int): IdxFile {
        require(indexFileId in 0 until archiveCount) {
            "Can not open index file because $FILE_NAME.${IdxFile.EXTENSION}$indexFileId does not exist."
        }
        return IdxFile.open(indexFileId, root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$indexFileId"))
    }

    /**
     * Reads data from the [Js5DiskStore].
     */
    fun read(indexFile: IdxFile, containerId: Int): ByteBuf {
        require(indexFile.id in 0 until archiveCount || indexFile == masterIdxFile) {
            "Can not read data because $FILE_NAME.${IdxFile.EXTENSION}${indexFile.id} does not exist."
        }
        val index = indexFile.read(containerId)
        if(index.dataSize == 0) {
            logger.warn {
                "Could not read index file ${indexFile.id} container $containerId because the index does not exist"
            }
            return Unpooled.EMPTY_BUFFER
        } else {
            logger.debug { "Reading index file ${indexFile.id} container $containerId" }
        }
        return dat2File.read(indexFile.id, containerId, index)
    }

    /**
     * Writes data to the [Js5DiskStore].
     */
    fun write(indexFile: IdxFile, containerId: Int, data: ByteBuf) {
        require(indexFile.id in 0 until archiveCount || indexFile == masterIdxFile) {
            "Can not write data because $FILE_NAME.${IdxFile.EXTENSION}${indexFile.id} does not exist."
        }
        logger.debug { "Writing index file ${indexFile.id} container $containerId" }
        val overWriteIndex = indexFile.containsIndex(containerId)
        val firstSegNumber = if(overWriteIndex) {
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
    fun remove(indexFile: IdxFile, containerId: Int) {
        require(indexFile.id in 0 until archiveCount || indexFile == masterIdxFile) {
            "Can not remove data because $FILE_NAME.${IdxFile.EXTENSION}${indexFile.id} does not exist."
        }
        logger.debug { "Removing index file ${indexFile.id} container $containerId" }
        indexFile.remove(containerId)
    }

        override fun close() {
            logger.debug { "Closing Js5DiskStore at $root" }
            dat2File.close()
            masterIdxFile.close()
        }

        companion object {
        /**
         * The default cache file name.
         */
        private const val FILE_NAME = "main_file_cache"

        /**
         * The master [IdxFile.id].
         */
        const val MASTER_INDEX = 255

        /**
         * Opens a [Js5DiskStore].
         *
         * @param root The folder where the cache is located.
         */
        fun open(root: Path): Js5DiskStore {
            require(Files.isDirectory(root)) { "$root is not a directory or doesn't exist." }
            val dataPath = root.resolve("$FILE_NAME.${Dat2File.EXTENSION}")
            if(Files.exists(dataPath)) {
                logger.debug { "Found .dat2 file" }
            } else {
                logger.debug { "Could not find .dat2 file\nCreated empty .dat2 file" }
                Files.createFile(dataPath)
                logger.debug { "Created empty .dat2 file\"" }
            }
            val dataFile = Dat2File.open(dataPath)
            val masterIndexPath = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$MASTER_INDEX")
            if(Files.exists(masterIndexPath)) {
                logger.debug { "Found .idx255 file" }
            } else {
                logger.debug { "Could not find .idx255 file" }
                Files.createFile(masterIndexPath)
                logger.debug { "Created empty .idx255 file" }
            }
            val masterIndexFile = IdxFile.open(MASTER_INDEX, masterIndexPath)
            var archiveCount = 0
            for (indexFileId in 0 until MASTER_INDEX) {
                val indexPath = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$indexFileId")
                if(!Files.exists(indexPath)) {
                    archiveCount = indexFileId
                    break
                }
            }
            logger.debug { "Created disk store with archive count $archiveCount" }
            return Js5DiskStore(root, dataFile, masterIndexFile, archiveCount)
        }
    }
}