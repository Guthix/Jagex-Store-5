package io.guthix.cache.js5.store

import java.nio.ByteBuffer

interface ContainerReader : AutoCloseable {
    val archiveCount: Int

    fun read(indexFileId: Int, containerId: Int): ByteBuffer
}