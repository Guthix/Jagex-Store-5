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
package io.guthix.cache.js5.downloader

import io.guthix.cache.js5.Js5ArchiveChecksum
import io.guthix.cache.js5.Js5ArchiveSettings
import io.guthix.cache.js5.Js5CacheChecksum
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.disk.Js5DiskStore
import io.guthix.cache.js5.container.net.Js5SocketReader
import io.guthix.cache.js5.util.crc
import io.guthix.cache.js5.util.whirlPoolHash
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Downloads a cache from a JS5 server.
 *
 * Arguments:
 *  -o= The location where the cache should be stored
 *  -a= The address of the JS5 server
 *  -r= The game revision of the JS5 server
 *  -c= The amount of archives to download
 *  -p= The port to connect to
 *  -v  Whether to store versions at the end of every file
 */
fun main(args: Array<String>) {
    var outputDir: Path? = null
    var address: String? = null
    var port: Int? = null
    var revision: Int? = null
    var includeVersions = false
    for(arg in args) {
        when {
            arg.startsWith("-o=") -> outputDir = Path.of(arg.substring(3))
            arg.startsWith("-a=") -> address = arg.substring(3)
            arg.startsWith("-r=") -> revision = arg.substring(3).toInt()
            arg.startsWith("-p=") -> port = arg.substring(3).toInt()
            arg.startsWith("-v") -> includeVersions = true
        }
    }
    requireNotNull(outputDir) { "No output directory specified to store the cache. Pass -o=DIR as an argument." }
    requireNotNull(address) {
        "No address has been specified to download the cache from. Pass -a=ADDRESS as an argument."
    }
    requireNotNull(port) { "No port has been specified to download the cache from. Pass -p=PORT as an argument." }
    requireNotNull(revision) { "No game revision has been specified. Pass -r=REVISION as an argument." }
    if(!Files.isDirectory(outputDir)) Files.createDirectory(outputDir)
    val ds = Js5DiskStore.open(outputDir)
    val sr = Js5SocketReader.open(
        sockAddr = InetSocketAddress(address, port),
        revision = revision,
        priorityMode = false
    )
    val checksum = Js5CacheChecksum.decode(Js5Container.decode(
        sr.read(Js5DiskStore.MASTER_INDEX, Js5DiskStore.MASTER_INDEX)
    ).data)
    val settingsData = Array(checksum.archiveChecksums.size) {
        sr.read(Js5DiskStore.MASTER_INDEX, it)
    }
    val archiveSettings = checkSettingsData(checksum, settingsData)
    settingsData.mapIndexed { archiveId, data ->
        ds.write(ds.masterIndex, archiveId, data)
    }

    val readThread = Thread { // start thread that sends requests
        archiveSettings.forEachIndexed { archiveId, archiveSettings ->
            archiveSettings.groupSettings.forEach { (groupId, _) ->
                sr.sendFileRequest(archiveId, groupId)
                Thread.sleep(25) // prevent remote from closing the connection
            }
        }
        logger.info("Done sending requests")
    }

    val writeThread = Thread { // start thread that reads requests
        archiveSettings.forEachIndexed { archiveId, archiveSettings ->
            val idxFile = if(!ds.idxFileExists(archiveId)) ds.createIdxFile() else ds.openIdxFile(archiveId)
            archiveSettings.groupSettings.forEach { (_, groupSettings) ->
                val response = sr.readFileResponse()
                if(response.data.crc() != groupSettings.crc) throw IOException(
                    "Response index file ${response.indexFileId} container ${response.containerId} corrupted."
                )
                val writeData = if(groupSettings.version != -1 && includeVersions) { // add version if exists
                    Unpooled.buffer(response.data.capacity() + 2).run {
                        writeBytes(response.data)
                        writeShort(groupSettings.version)
                    }
                } else response.data
                ds.write(idxFile, response.containerId, writeData)
            }
        }
        logger.info("Done writing responses")
    }
    readThread.start()
    writeThread.start()
    readThread.join()
    writeThread.join()
}

private fun checkSettingsData(
    readChecksum: Js5CacheChecksum,
    settingsData: Array<ByteBuf>
): MutableList<Js5ArchiveSettings> {
    val newFormat = readChecksum.newFormat
    val containsWhirlool = readChecksum.containsWhirlpool
    val archiveSettings = mutableListOf<Js5ArchiveSettings>()
    val archiveChecksums = settingsData.map { data ->
        val settings = Js5ArchiveSettings.decode(
            Js5Container.decode(data)
        )
        archiveSettings.add(settings)
        val whirlpoolHash = if(containsWhirlool) data.whirlPoolHash() else null
        val fileCount = if(newFormat) settings.groupSettings.size else null
        val uncompressedSize = if(newFormat) settings.groupSettings.values.sumBy {
            it.sizes?.uncompressed ?: 0
        } else null
        data.readerIndex(0)
        Js5ArchiveChecksum(data.crc(), settings.version ?: 0, fileCount, uncompressedSize, whirlpoolHash)
    }.toTypedArray()
    val calcChecksum = Js5CacheChecksum(archiveChecksums)
    if(readChecksum != calcChecksum) throw IOException(
        "Checksum does not match, archive settings are corrupted."
    )
    return archiveSettings
}