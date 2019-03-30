package io.guthix.cache.fs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JagexCacheTest {
    @Test
    @ExperimentalUnsignedTypes
    fun readWriteArchiveSequentialFilesTest(@TempDir cacheDir: File) {
        val filesToWrite = mapOf<Int, ByteBuffer>(
            1 to ByteBuffer.allocate(8).apply { repeat(8) { put(255.toByte())} },
            2 to ByteBuffer.allocate(16).apply { repeat(16) { put(18.toByte())} },
            3 to ByteBuffer.allocate(3).apply { repeat(3) { put(0.toByte())} }
        )
        JagexCache(cacheDir).use { cache ->
            cache.writeArchive(
                dictionaryId = 0,
                archiveId = 0,
                fileBuffers = filesToWrite
            )
            val archive = cache.readArchive(dictionaryId = 0, archiveId = 0)
            val readFiles = mutableMapOf<Int, ByteBuffer>()
            archive.files.forEach { fileId, file ->
                readFiles[fileId] = file.data
            }
            filesToWrite.forEach { it.value.flip() }
            assertEquals(filesToWrite, readFiles)
        }
    }
}