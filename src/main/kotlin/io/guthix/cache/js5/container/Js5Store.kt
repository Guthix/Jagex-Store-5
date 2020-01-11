package io.guthix.cache.js5.container

import io.netty.buffer.ByteBuf

/**
 * [Js5Container] reader and writer.
 */
interface Js5Store : Js5ReadStore, Js5WriteStore {
    companion object {
        /**
         * The master index file id that contains the settings.
         */
        const val MASTER_INDEX = 255
    }
}

/**
 * [Js5Container] reader.
 */
interface Js5ReadStore : AutoCloseable {
    val archiveCount: Int

    fun read(indexId: Int, containerId: Int): ByteBuf
}

/**
 * [Js5Container] writer.
 */
interface Js5WriteStore : AutoCloseable {
    var archiveCount: Int

    fun write(indexId: Int, containerId: Int, data: ByteBuf)

    fun remove(indexId: Int, containerId: Int)
}