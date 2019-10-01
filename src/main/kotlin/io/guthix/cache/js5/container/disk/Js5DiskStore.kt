/*
 * Copyright (C) 2019 Guthix
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.guthix.cache.js5.container.disk

import mu.KotlinLogging
import java.io.IOException
import io.guthix.cache.js5.container.Js5ContainerReaderWriter
import io.guthix.cache.js5.container.Js5Container
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

private val logger = KotlinLogging.logger {}

/**
 * A JS5 filesystem on disk for reading [Js5Container]s.
 *
 * Each filesystem contains of 1 data file 0 or more archive index files and a master index. The actually data is stored
 * in the data file. The index files serve as pointers to data in the data file. The master index is a special index
 * pointing to meta data of archives. The archive indices should be sequentially numbered starting from 0.
 *
 * @property dataFile The data channel of the file system.
 * @property archiveIndexFiles The archive index channels indexed by the archive id
 * @property masterIndexFile The master index channel that contains meta-data for the archives
 */
class Js5DiskStore private constructor(
    private val root: Path,
    private val dataFile: Dat2File,
    private val archiveIndexFiles: MutableList<IdxFile>,
    private val masterIndexFile: IdxFile
) : Js5ContainerReaderWriter {
    override val archiveCount get() = archiveIndexFiles.size

    override fun read(indexFileId: Int, containerId: Int): ByteBuf {
        require(indexFileId == MASTER_INDEX || indexFileId in 0 until archiveIndexFiles.size) {
            "Can not read from index file $indexFileId because it does not exist."
        }
        val index = if(indexFileId == MASTER_INDEX) {
            masterIndexFile.read(containerId)
        } else {
            archiveIndexFiles[indexFileId].read(containerId)
        }
        if(index.dataSize == 0) {
            logger.warn { "Could not read index file $indexFileId container $containerId because index does not exist" }
            return Unpooled.EMPTY_BUFFER
        } else {
            logger.debug { "Reading index file $indexFileId container $containerId" }
        }
        return dataFile.read(indexFileId, containerId, index)
    }

    override fun write(indexFileId: Int, containerId: Int, data: ByteBuf) {
        require(indexFileId == MASTER_INDEX || indexFileId in 0..archiveIndexFiles.size) {
            "Can not write to or create index file $indexFileId because it is not sequential."
        }
        val indexFile = if(indexFileId == MASTER_INDEX) {
            masterIndexFile
        } else {
            archiveIndexFiles.getOrElse(indexFileId) { // create new file
                val indexPath = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$indexFileId")
                Files.createFile(indexPath)
                val indexFile = IdxFile.open(indexPath)
                archiveIndexFiles.add(indexFile)
                logger.info { "Created empty .idx$indexFileId file" }
                indexFile
            }
        }
        logger.debug { "Writing index file $indexFileId container $containerId" }
        val overWriteIndex = indexFile.containsIndex(containerId)
        val firstSegNumber = if(overWriteIndex) {
            indexFile.read(containerId).sectorNumber
        } else {
            ceil(dataFile.size.toDouble() / Sector.SIZE).toInt() // last sector of the data file
        }
        val index = Index(data.readableBytes(), firstSegNumber)
        indexFile.write(containerId, index)
        dataFile.write(indexFileId, containerId, index, data)
    }

    override fun remove(indexFileId: Int, containerId: Int) = if(indexFileId == MASTER_INDEX) {
        masterIndexFile.remove(containerId)
    } else {
        archiveIndexFiles[indexFileId].remove(containerId)
    }

    override fun close() {
        logger.debug { "Closing JS5 filesystem at $root" }
        dataFile.close()
        archiveIndexFiles.forEach { it.close() }
        masterIndexFile.close()
    }

    companion object {
        /**
         * The default cache file name.
         */
        private const val FILE_NAME = "main_file_cache"

        /**
         * The master index.
         */
        const val MASTER_INDEX = 255

        fun open(root: Path): Js5DiskStore {
            if(!Files.isDirectory(root)) throw IOException("$root is not a directory or doesn't exist.")
            val dataPath = root.resolve("$FILE_NAME.${Dat2File.EXTENSION}")
            if(Files.exists(dataPath)) {
                logger.debug { "Found .dat2 file" }
            } else {
                logger.debug { "Could not find .dat2 file\nCreated empty .dat2 file" }
                Files.createFile(dataPath)
                logger.debug { "Created empty .dat2 file\"" }
            }
            val dataFile = Dat2File.open(dataPath)
            val archiveIndexFiles: MutableList<IdxFile> = mutableListOf()
            for (indexFileId in 0 until MASTER_INDEX) {
                val indexPath = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$indexFileId")
                if(!Files.exists(indexPath))  {
                    logger.debug { "Found $indexFileId index file${if(indexFileId != 1) "s" else ""}" }
                    break
                }
                archiveIndexFiles.add(IdxFile.open(indexPath))
            }
            val masterIndexPath = root.resolve("$FILE_NAME.${IdxFile.EXTENSION}$MASTER_INDEX")
            if(Files.exists(masterIndexPath)) {
                logger.debug { "Found .idx255 file" }
            } else {
                logger.debug { "Could not find .idx255 file" }
                Files.createFile(masterIndexPath)
                logger.debug { "Created empty .idx255 file" }
            }
            val masterIndexFile = IdxFile.open(masterIndexPath)
            return Js5DiskStore(root, dataFile, archiveIndexFiles, masterIndexFile)
        }
    }
}
