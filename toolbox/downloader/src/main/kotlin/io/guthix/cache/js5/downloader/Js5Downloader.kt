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
package io.guthix.cache.js5.downloader

import io.guthix.cache.js5.Js5ArchiveValidator
import io.guthix.cache.js5.Js5ArchiveSettings
import io.guthix.cache.js5.Js5CacheValidator
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.Js5Store
import io.guthix.cache.js5.container.disk.Js5DiskStore
import io.guthix.cache.js5.container.net.Js5NetReader
import io.guthix.cache.js5.util.crc
import io.guthix.cache.js5.util.whirlPoolHash
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import me.tongfei.progressbar.DelegatingProgressBarConsumer
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import mu.KotlinLogging
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

object Js5Downloader {
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
    @JvmStatic
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
        logger.info { "Downloading cache to $outputDir" }
        if(!Files.isDirectory(outputDir)) outputDir.toFile().mkdirs()
        val ds = Js5DiskStore.open(outputDir)
        val sr = Js5NetReader.open(
            sockAddr = InetSocketAddress(address, port),
            revision = revision,
            priorityMode = false
        )
        logger.info { "Downloading validator" }
        val validator = Js5CacheValidator.decode(Js5Container.decode(
            sr.read(Js5Store.MASTER_INDEX, Js5Store.MASTER_INDEX)
        ).data, whirlpoolIncluded = false, sizeIncluded = false)
        logger.info { "Downloading archive settings" }
        val archiveCount = validator.archiveValidators.size
        val progressBarSettings = ProgressBarBuilder()
            .setInitialMax(archiveCount.toLong())
            .setTaskName("Downloader")
            .setStyle(ProgressBarStyle.ASCII)
            .setConsumer(DelegatingProgressBarConsumer(logger::info))
            .build()
        progressBarSettings.extraMessage = "Downloading settings"
        val settingsData = progressBarSettings.use { pb ->
            Array(archiveCount) {
                pb.step()
                sr.read(Js5Store.MASTER_INDEX, it)
            }
        }

        val archiveSettings = checkSettingsData(validator, settingsData)
        settingsData.mapIndexed { archiveId, data ->
            ds.write(Js5Store.MASTER_INDEX, archiveId, data)
        }
        val amountOfDownloads = archiveSettings.sumBy { it.groupSettings.keys.size }
        logger.info { "Downloading archives" }
        val readThread = Thread { // start thread that sends requests
            archiveSettings.forEachIndexed { archiveId, archiveSettings ->
                archiveSettings.groupSettings.forEach { (groupId, _) ->
                    sr.sendFileRequest(archiveId, groupId)
                    Thread.sleep(20) // requesting to fast makes the server close the connection
                }
            }
            logger.info("Done sending requests")
        }
        val writeThread = Thread { // start thread that reads requests
            val progressBarGroups = ProgressBarBuilder()
                .setInitialMax(amountOfDownloads.toLong())
                .setTaskName("Downloader")
                .setStyle(ProgressBarStyle.ASCII)
                .setConsumer(DelegatingProgressBarConsumer(logger::info))
                .build()
            progressBarGroups.use { pb ->
                archiveSettings.forEachIndexed { archiveId, archiveSettings ->
                    pb.extraMessage = "Downloading archive $archiveId"
                    archiveSettings.groupSettings.forEach { (_, groupSettings) ->
                        val response = sr.readFileResponse()
                        if(response.data.crc() != groupSettings.crc) throw IOException(
                            "Response index file ${response.indexFileId} container ${response.containerId} corrupted."
                        )
                        val writeData = if(groupSettings.version != -1 && includeVersions) { // add version if exists
                            val versionBuffer = Unpooled.buffer(2).apply { writeShort(groupSettings.version) }
                            Unpooled.compositeBuffer(2).addComponents(true, response.data, versionBuffer)
                        } else response.data
                        pb.step()
                        ds.write(archiveId, response.containerId, writeData)
                    }
                }
            }
            logger.info("Done writing responses")
        }
        readThread.start()
        writeThread.start()
        readThread.join()
        writeThread.join()
        ds.close()
        sr.close()
    }

    private fun checkSettingsData(
        readValidator: Js5CacheValidator,
        settingsData: Array<ByteBuf>
    ): MutableList<Js5ArchiveSettings> {
        val newFormat = readValidator.newFormat
        val containsWhirlool = readValidator.containsWhirlpool
        val archiveSettings = mutableListOf<Js5ArchiveSettings>()
        val archiveValidators = settingsData.map { data ->
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
            Js5ArchiveValidator(data.crc(), settings.version ?: 0, whirlpoolHash, fileCount, uncompressedSize)
        }.toTypedArray()
        val calcValidator = Js5CacheValidator(archiveValidators)
        if(readValidator != calcValidator) throw IOException(
            "Checksum does not match, archive settings are corrupted."
        )
        return archiveSettings
    }
}

