package io.guthix.cache.js5.store

import java.nio.ByteBuffer

interface ContainerWriter : AutoCloseable {
    fun write(indexFileId: Int, containerId: Int, data: ByteBuffer)
}