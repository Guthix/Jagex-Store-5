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
package io.guthix.cache.js5.container.filesystem

import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import io.guthix.cache.js5.container.Js5ContainerReaderWriter
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.Js5Group
import io.guthix.cache.js5.Js5ArchiveSettings

private val logger = KotlinLogging.logger {}

/**
 * A JS5 filesystem on disk for reading [Js5Container]s.
 *
 * Each filesystem contains of 1 data file 0 or more archive index files and a master index. The actually data is stored
 * in the data file. The index files serve as pointers to data in the data file. The master index is a special index
 * pointing to meta data of archives. The archive indices should be sequentially numbered starting from 0.
 *
 * @property directory root directory where the files are stored.
 */
class Js5FileSystem(private val directory: File) : Js5ContainerReaderWriter {
    /**
     * The data channel where all the data is stored.
     */
    private val dataChannel: Dat2Channel

    /**
     * The archive index channels where pointers are stored to [Js5Group] data.
     */
    private val archiveIndexChannels: MutableList<IDXChannel> = mutableListOf()

    /**
     * The master index channel where pointers to [Js5ArchiveSettings] are stored.
     */
    private val masterIndexChannel: IDXChannel

    /**
     * The amount of archives in this filesystem.
     */
    override val archiveCount get() = archiveIndexChannels.size

    init {
        if(!directory.isDirectory) throw IOException("$directory is not a directory or doesn't exist.")
        val dataFile = directory.resolve("$FILE_NAME.$DAT2_FILE_EXTENSION")
        if(!dataFile.createNewFile()) {
            logger.info("Found .dat2 file")
        } else {
            logger.info("Could not find .dat2 file")
            logger.info("Created empty .dat2 file")
        }
        dataChannel = Dat2Channel(
            RandomAccessFile(
                dataFile,
                accessMode
            ).channel
        )
        for (indexFileId in 0 until MASTER_INDEX) {
            val indexFile = directory.resolve("$FILE_NAME.$IDX_FILE_EXTENSION$indexFileId")
            if(!indexFile.isFile)  {
                logger.info("Found $indexFileId index ${if(indexFileId == 1) "file." else "files"}")
                break
            }
            archiveIndexChannels.add(
                IDXChannel(
                    RandomAccessFile(
                        indexFile,
                        accessMode
                    ).channel
                )
            )
        }
        val masterIndexFile = directory.resolve("$FILE_NAME.$IDX_FILE_EXTENSION$MASTER_INDEX")
        if(!masterIndexFile.createNewFile()) {
            logger.info("Found .idx255 file")
        } else {
            logger.info("Could not find .idx255 file")
            logger.info("Created empty .idx255 file")
        }
        masterIndexChannel = IDXChannel(
            RandomAccessFile(
                masterIndexFile,
                accessMode
            ).channel
        )
    }

    /**
     * Reads container data from the filesystem.
     *
     * @param indexFileId The index file to read from.
     * @param containerId The container to read.
     */
    override fun read(indexFileId: Int, containerId: Int): ByteArray {
        logger.info("Reading index file $indexFileId container $containerId")
        if((indexFileId < 0 || indexFileId >= archiveIndexChannels.size) && indexFileId != MASTER_INDEX) {
            throw IOException("Index file does not exist.")
        }
        val index = if(indexFileId == MASTER_INDEX) {
            masterIndexChannel.read(containerId)
        } else {
            archiveIndexChannels[indexFileId].read(containerId)
        }
        return dataChannel.read(indexFileId, containerId, index)
    }

    /**
     * Writes container data to the filesystem.
     *
     * @param indexFileId The index file id to write to.
     * @param containerId The container id to write to.
     */
    override fun write(indexFileId: Int, containerId: Int, data: ByteArray) {
        logger.info("Writing index file $indexFileId container $containerId")
        if(indexFileId >= archiveIndexChannels.size && indexFileId != MASTER_INDEX) {
            if(indexFileId == archiveIndexChannels.size) {
                val indexFile = directory.resolve("$FILE_NAME.$IDX_FILE_EXTENSION$indexFileId")
                indexFile.createNewFile()
                archiveIndexChannels.add(
                    IDXChannel(
                        RandomAccessFile(
                            indexFile,
                            accessMode
                        ).channel
                    )
                )
                logger.info("Created empty .idx$indexFileId file")
            } else {
                throw IOException("Index file with id $indexFileId does not exist and could not be created.")
            }
        }
        val indexChannel = if(indexFileId == MASTER_INDEX) masterIndexChannel else archiveIndexChannels[indexFileId]
        val shouldOverwrite = indexChannel.containsIndex(containerId)
        val firstSegmentPos = if(shouldOverwrite) {
            indexChannel.read(containerId).segmentNumber
        } else {
            (dataChannel.size / Segment.SIZE).toInt()
        }
        val index = Index(data.size, firstSegmentPos)
        indexChannel.write(containerId, index)
        dataChannel.write(indexFileId, containerId, index, data)
    }

    override fun close() {
        dataChannel.close()
        archiveIndexChannels.forEach { it.close() }
        masterIndexChannel.close()
    }

    companion object {
        /**
         * The accessMode for the file channels.
         */
        private const val accessMode = "rw"

        /**
         * The data file extensions.
         */
        private const val DAT2_FILE_EXTENSION = "dat2"

        /**
         * The index file extensions.
         */
        private const val IDX_FILE_EXTENSION = "idx"

        /**
         * The default cache file name.
         */
        private const val FILE_NAME = "main_file_cache"

        /**
         * The master index.
         */
        const val MASTER_INDEX = 255
    }
}
