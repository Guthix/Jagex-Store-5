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

import io.guthix.cache.js5.Js5ArchiveSettings
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.filesystem.Js5FileSystem
import io.guthix.cache.js5.container.net.Js5SocketReader
import io.guthix.cache.js5.util.crc
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress

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
    var outputDir: File? = null
    var address: String? = null
    var port: Int? = null
    var revision: Int? = null
    var archiveCount: Int? = null
    var includeVersions = false
    for(arg in args) {
        when {
            arg.startsWith("-o=") -> outputDir = File(arg.substring(3))
            arg.startsWith("-a=") -> address = arg.substring(3)
            arg.startsWith("-r=") -> revision = arg.substring(3).toInt()
            arg.startsWith("-c=") -> archiveCount = arg.substring(3).toInt()
            arg.startsWith("-p=") -> port =  arg.substring(3).toInt()
            arg.startsWith("-v") -> includeVersions = true
        }
    }
    if(outputDir == null) throw IllegalArgumentException(
        "No output directory specified to store the cache. Pass -o=DIR as an argument."
    )
    if(address == null) throw IllegalArgumentException(
        "No address has been specified to download the cache from. Pass -a=ADDRESS as an argument."
    )
    if(port == null) throw IllegalArgumentException(
        "No port has been specified to download the cache from. Pass -p=PORT as an argument."
    )
    if(revision == null) throw IllegalArgumentException(
        "No game revision has been specified. Pass -r=REVISION as an argument."
    )
    if(archiveCount == null) throw IllegalArgumentException(
        "No archive count has been specified. Pass -c=ARCHIVECOUNT as an argument."
    )
    val js5FileSystem = Js5FileSystem(outputDir)
    val js5SocketReader = Js5SocketReader(
        sockAddr = InetSocketAddress(address, port),
        revision = revision,
        priorityMode = true,
        archiveCount = archiveCount
    )
    val settingsData = Array(archiveCount) { archiveId ->
        js5SocketReader.read(Js5FileSystem.MASTER_INDEX, archiveId)
    }
    settingsData.forEachIndexed { archiveId, data ->
        js5FileSystem.write(Js5FileSystem.MASTER_INDEX, archiveId, data)
    }
    val settings = settingsData.map { data ->
        Js5ArchiveSettings.decode(Js5Container.decode(data))
    }
    val readThread = Thread { // start thread that sends requests
        settings.forEachIndexed { archiveId, archiveSettings ->
            archiveSettings.js5GroupSettings.forEach { (groupId, _) ->
                js5SocketReader.sendFileRequest(archiveId, groupId)
                Thread.sleep(25) // prevent remote from closing the connection
            }
        }
        logger.info("Done sending requests")
    }
    val writeThread = Thread { // start thread that reads requests
        settings.forEachIndexed { _, archiveSettings ->
            archiveSettings.js5GroupSettings.forEach { (_, groupSettings) ->
                val response = js5SocketReader.readFileResponse()
                if(response.data.crc() != groupSettings.crc) throw IOException(
                    "Response index file ${response.indexFileId} container ${response.containerId} corrupted."
                )
                val writeData = if(groupSettings.version != -1 && includeVersions) { // add version if exists
                    Unpooled.buffer(response.data.capacity() + 2).run {
                        writeBytes(response.data)
                        writeShort(groupSettings.version)
                    }
                } else response.data
                js5FileSystem.write(response.indexFileId, response.containerId, writeData)
            }
        }
        logger.info("Done writing responses")
    }
    readThread.start()
    writeThread.start()
    readThread.join()
    writeThread.join()
}