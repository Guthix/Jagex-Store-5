package io.guthix.cache.js5.verifier

import io.guthix.cache.js5.Js5ArchiveSettings
import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.filesystem.Js5FileSystem
import io.guthix.cache.js5.util.crc
import mu.KotlinLogging
import java.io.File
import java.util.zip.ZipException

private val logger = KotlinLogging.logger {}

/**
 * Verifies if the data in a cache is consistent with its settings data.
 *
 * Arguments:
 *  -o= The location where the cache is stored.
 *  -e= The archives to exclude from verifying.
 */
fun main(args: Array<String>) {
    var inputDir: File? = null
    val excludedArchives = mutableListOf<Int>()
    for(arg in args) {
        when {
            arg.startsWith("-i=") -> inputDir = File(arg.substring(3))
            arg.startsWith("-e=") -> excludedArchives.add(arg.substring(3).toInt())
        }
    }
    if(inputDir == null) throw IllegalArgumentException(
        "No output directory specified to read the cache from. Pass -i=DIR as an argument."
    )
    val fileSystem = Js5FileSystem(inputDir)
    val settings = Array(fileSystem.archiveCount) { archiveId ->
        Js5ArchiveSettings.decode(Js5Container.decode(fileSystem.read(Js5FileSystem.MASTER_INDEX, archiveId)))
    }
    settings.forEachIndexed { archiveId, archiveSettings ->
        if(!excludedArchives.contains(archiveId)) {
            archiveSettings.js5GroupSettings.forEach { (groupId, groupSettings) ->
                val data = fileSystem.read(archiveId, groupId)
                if(data.crc() != groupSettings.crc) logger.info(
                    "CRC does not match for archive $archiveId group $groupId"
                )
                try {
                    val container = Js5Container.decode(data)
                    if(container.isVersioned && container.version != groupSettings.version)  logger.info(
                        "Version does not match for archive $archiveId group $groupId container: ${container.version} settings ${groupSettings.version}"
                    )
                    val sizes = Js5Container.sizeOfContainer(data)
                    if(groupSettings.sizes != null && sizes != groupSettings.sizes) logger.info(
                        "Sizes do not match for archive $archiveId group $groupId container: $sizes settings ${groupSettings.sizes}"
                    )
                } catch (ex: NegativeArraySizeException) {
                    logger.error("No encryption key provided for archive $archiveId group $groupId")
                } catch (ex: ZipException) {
                    logger.error("No encryption key provided for archive $archiveId group $groupId")
                }
            }
            logger.info("Verified archive $archiveId")
        }
    }
}