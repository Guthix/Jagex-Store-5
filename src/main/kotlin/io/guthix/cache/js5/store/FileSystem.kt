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
package io.guthix.cache.js5.store

import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

class FileSystem(val directory: File) : AutoCloseable {
    private val dataChannel: Dat2Channel

    private val archiveIndexChannels: MutableList<IDXChannel> = mutableListOf()

    private val masterIndexChannels: IDXChannel

    internal val archiveCount get() = archiveIndexChannels.size

    init {
        if(!directory.isDirectory) throw IOException("$directory is not a directory or doesn't exist.")
        val dataFile = directory.resolve("$FILE_NAME.$DAT2_FILE_EXTENSION")
        if(!dataFile.createNewFile()) {
            logger.info("Found .dat2 file")
        } else {
            logger.info("Could not find .dat2 file")
            logger.info("Created empty .dat2 file")
        }
        dataChannel = Dat2Channel(RandomAccessFile(dataFile, accessMode).channel)
        for (indexFileId in 0 until MASTER_INDEX) {
            val indexFile = directory.resolve("$FILE_NAME.$IDX_FILE_EXTENSION$indexFileId")
            if(!indexFile.isFile)  {
                logger.info("Found $indexFileId index ${if(indexFileId == 1) "file." else "files"}")
                break
            }
            archiveIndexChannels.add(IDXChannel(RandomAccessFile(indexFile, accessMode).channel))
        }
        val masterIndexFile = directory.resolve("$FILE_NAME.$IDX_FILE_EXTENSION$MASTER_INDEX")
        if(!masterIndexFile.createNewFile()) {
            logger.info("Found .idx255 file")
        } else {
            logger.info("Could not find .idx255 file")
            logger.info("Created empty .idx255 file")
        }
        masterIndexChannels = IDXChannel(RandomAccessFile(masterIndexFile, accessMode).channel)
    }

    @ExperimentalUnsignedTypes
    internal fun read(indexFileId: Int, containerId: Int): ByteBuffer {
        logger.info("Reading index file $indexFileId container $containerId")
        if((indexFileId < 0 || indexFileId >= archiveIndexChannels.size) && indexFileId != MASTER_INDEX) {
            throw IOException("Index file does not exist.")
        }
        val index = if(indexFileId == MASTER_INDEX) {
            masterIndexChannels.read(containerId)
        } else {
            archiveIndexChannels[indexFileId].read(containerId)
        }
        return dataChannel.read(indexFileId, index, containerId)
    }

    @ExperimentalUnsignedTypes
    internal fun write(indexFileId: Int, containerId: Int, data: ByteBuffer) {
        logger.info("Writing index file $indexFileId container $containerId")
        if(indexFileId < 0 || indexFileId >= archiveIndexChannels.size && indexFileId != MASTER_INDEX) {
            if(indexFileId == archiveIndexChannels.size) {
                val indexFile = directory.resolve("$FILE_NAME.$IDX_FILE_EXTENSION$indexFileId")
                indexFile.createNewFile()
                archiveIndexChannels.add(IDXChannel(RandomAccessFile(indexFile, accessMode).channel))
                logger.info("Created empty .idx$indexFileId file")
            } else {
                throw IOException("Index file with id $indexFileId does not exist and could not be created.")
            }
        }
        val indexChannel = if(indexFileId == MASTER_INDEX) masterIndexChannels else archiveIndexChannels[indexFileId]
        val shouldOverwrite = indexChannel.containsIndex(containerId)
        val firstSegmentPos = if(shouldOverwrite) {
            indexChannel.read(containerId).segmentNumber
        } else {
            (dataChannel.size / Segment.SIZE).toInt()
        }
        val index = Index(data.limit(), firstSegmentPos)
        indexChannel.write(containerId, index)
        dataChannel.write(indexFileId, containerId, index, data)
    }

    override fun close() {
        dataChannel.close()
        archiveIndexChannels.forEach { it.close() }
        masterIndexChannels.close()
    }

    companion object {
        private const val accessMode = "rw"
        private const val DAT2_FILE_EXTENSION = "dat2"
        private const val IDX_FILE_EXTENSION = "idx"
        private const val FILE_NAME = "main_file_cache"
        const val MASTER_INDEX = 255
    }
}
