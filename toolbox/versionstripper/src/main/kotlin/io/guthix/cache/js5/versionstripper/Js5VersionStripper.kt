package io.guthix.cache.js5.versionstripper

import io.guthix.cache.js5.Js5ArchiveSettings
import io.guthix.cache.js5.container.Js5Compression
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.Js5Store
import io.guthix.cache.js5.container.disk.Js5DiskStore
import io.netty.buffer.ByteBuf
import me.tongfei.progressbar.DelegatingProgressBarConsumer
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import mu.KotlinLogging
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

object Js5VersionStripper {
    @JvmStatic
    fun main(args: Array<String>) {
        var cacheDir: Path? = null
        for (arg in args) {
            when {
                arg.startsWith("-i=") -> cacheDir = Path.of(arg.substring(3))
            }
        }
        require(cacheDir != null) { "No cache directory specified to read the cache. Pass -i=DIR as an argument." }
        val store = Js5DiskStore.open(cacheDir)
        val archiveSettings = mutableMapOf<Int, Js5ArchiveSettings>()
        for(archiveId in 0 until store.archiveCount) {
            val archiveSettingsData = store.read(Js5Store.MASTER_INDEX, archiveId)
            archiveSettings[archiveId] = Js5ArchiveSettings.decode(Js5Container.decode(archiveSettingsData))
        }
        val grouupCount = archiveSettings.values.sumBy { it.groupSettings.keys.size }
        val progressBar = ProgressBarBuilder()
            .setInitialMax(grouupCount.toLong())
            .setTaskName("Remover")
            .setStyle(ProgressBarStyle.ASCII)
            .setConsumer(DelegatingProgressBarConsumer(logger::info))
            .build()
        progressBar.use { pb ->
            archiveSettings.forEach { (archiveId, archiveSettings) ->
                pb.extraMessage = "Removing from archive $archiveId"
                archiveSettings.groupSettings.forEach { (groupId, _) ->
                    if(archiveId != 5) {
                        val data = store.read(archiveId, groupId)
                        val version = Js5Container.decodeVersion(data.duplicate())
                        if(version != null) {
                            store.write(archiveId, groupId, data.slice(0, data.readableBytes() - Short.SIZE_BYTES))
                        }
                    }
                    pb.step()
                }
            }
        }
    }

    private fun Js5Container.Companion.decodeVersion(buf: ByteBuf): Int? {
        val compression = Js5Compression.getByOpcode(buf.readUnsignedByte().toInt())
        val compressedSize = buf.readInt()
        val indexAfterCompression = ENC_HEADER_SIZE + compression.headerSize + compressedSize
        buf.readerIndex(indexAfterCompression)
        return if(buf.readableBytes() >= 2) buf.readShort().toInt() else null
    }
}
