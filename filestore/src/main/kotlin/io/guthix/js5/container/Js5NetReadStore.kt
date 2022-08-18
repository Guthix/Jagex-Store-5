/*
 * Copyright 2018-2021 Guthix
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.guthix.js5.container

import io.guthix.js5.Js5ArchiveSettings
import io.guthix.js5.container.disk.Sector
import io.netty.buffer.ByteBuf
import io.netty.buffer.DefaultByteBufHolder
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.SocketChannel
import kotlin.experimental.xor
import kotlin.math.ceil

private val logger = KotlinLogging.logger {}

/**
 * Connection type used for establishing a JS5 connection.
 */
private const val JS5_CONNECTION_TYPE = 15

/**
 * A Js5 request to the server.
 */
private enum class Js5Request(val opcode: Int) {
    NORMAL_FILE_REQUEST(0),
    PRIORITY_FILE_REQUEST(1),
    ENCRYPTION_KEY_UPDATE(4);
}

/**
 * A file response from the server.
 */
public data class FileResponse(
    val indexFileId: Int,
    val containerId: Int,
    val data: ByteBuf
) : DefaultByteBufHolder(data)

/**
 * A [Js5ReadStore] for reading files for a remote JS5 connection. This reader only works for caches that don't use the
 * [Js5ArchiveSettings.Format.VERSIONED_LARGE] format.
 *
 * @property socketChannel The [SocketChannel] to read and write the data.
 * @property priorityMode Whether to make priority file requests.
 */
public class Js5NetReadStore private constructor(
    private val socketChannel: SocketChannel,
    public var priorityMode: Boolean = false
) : Js5ReadStore {
    /**
     * The encryption key.
     */
    private var xorKey: Byte = 0

    override val archiveCount: Int get() = throw UnsupportedOperationException(
        "Can't retrieve archive size from remote repository."
    )

    /**
     * Requests container data and blocks until the response arrives.
     *
     * @param indexId The index to request.
     * @param containerId the container to request.
     */
    override fun read(indexId: Int, containerId: Int): ByteBuf {
        sendFileRequest(indexId, containerId, priorityMode)
        return readFileResponse().data
    }

    /**
     * Reads the file response and blocks until it has been completely read.
     */
    public fun readFileResponse(): FileResponse {
        val headerBuffer = Unpooled.buffer(HEADER_RESPONSE_SIZE)
        while (headerBuffer.isWritable) headerBuffer.writeBytes(socketChannel, headerBuffer.writableBytes())
        headerBuffer.forEachByte { it xor xorKey; true }
        val indexFileId = headerBuffer.readUnsignedByte().toInt()
        val containerId = headerBuffer.readUnsignedShort()
        logger.trace { "Reading index file $indexFileId container $containerId" }
        val compression = Js5Compression.getByOpcode(headerBuffer.readUnsignedByte().toInt())
        val compressedSize = headerBuffer.readInt()
        val containerSize = compression.headerSize + compressedSize
        val dataBuf = Unpooled.buffer( // Read response data
            containerSize + ceil((containerSize - BYTES_AFTER_HEADER) / BYTES_AFTER_BLOCK.toDouble()).toInt()
        )
        val containerBuffer = Unpooled.buffer( // Container data
            Js5Container.ENC_HEADER_SIZE + containerSize
        )
        containerBuffer.writeByte(compression.opcode)
        containerBuffer.writeInt(compressedSize)
        while (dataBuf.isWritable) dataBuf.writeBytes(socketChannel, dataBuf.writableBytes())
        dataBuf.forEachByte { it xor xorKey; true }
        val headerDataSize = if (dataBuf.readableBytes() < BYTES_AFTER_HEADER) { // write all data after header
            dataBuf.readableBytes()
        } else {
            BYTES_AFTER_HEADER
        }
        containerBuffer.writeBytes(dataBuf.slice(0, headerDataSize))
        dataBuf.readerIndex(headerDataSize)
        var i = 0
        while (dataBuf.isReadable) {  // write other data
            var start = BYTES_AFTER_HEADER + i * (Sector.DATA_SIZE)
            start += 1 //skip 255
            val bytesToRead = dataBuf.readableBytes() - 1
            val blockDataSize = if (bytesToRead < BYTES_AFTER_BLOCK) {
                bytesToRead
            } else {
                BYTES_AFTER_BLOCK
            }
            containerBuffer.writeBytes(dataBuf.slice(start, blockDataSize))
            dataBuf.readerIndex(start + blockDataSize)
            i++
        }
        return FileResponse(indexFileId, containerId, containerBuffer)
    }

    /**
     * Updates the XOR encryption key and sends out a request to change the encryption key to the server.
     *
     * @param key The key to update.
     */
    public fun updateEncryptionKey(key: Byte) {
        xorKey = key
        sendEncryptionKeyChange(key)
    }

    /**
     * Sends the request to change the encryption key to the server.
     *
     * @param key The key to update.
     */
    private fun sendEncryptionKeyChange(key: Byte) {
        val buffer = Unpooled.buffer(REQUEST_PACKET_SIZE)
        buffer.writeByte(Js5Request.ENCRYPTION_KEY_UPDATE.opcode)
        buffer.writeByte(key.toInt())
        buffer.writeShort(0)
        buffer.readBytes(socketChannel, buffer.readableBytes())
    }

    /**
     * Sends a file request to the server.
     *
     * @param indexFileId The index id to request.
     * @param containerId The container id to request.
     * @param priority Whether to send a priority request.
     */
    public fun sendFileRequest(indexFileId: Int, containerId: Int, priority: Boolean = priorityMode) {
        logger.debug { "Requesting index file $indexFileId container $containerId" }
        val buf = Unpooled.buffer(REQUEST_PACKET_SIZE)
        if (priority) {
            buf.writeByte(Js5Request.PRIORITY_FILE_REQUEST.opcode)
        } else {
            buf.writeByte(Js5Request.NORMAL_FILE_REQUEST.opcode)
        }
        buf.writeByte(indexFileId)
        buf.writeShort(containerId)
        buf.readBytes(socketChannel, buf.readableBytes())
    }

    override fun close() {
        socketChannel.close()
    }

    public companion object {
        /**
         * Request packet size for any request.
         */
        private const val REQUEST_PACKET_SIZE = 4

        /**
         * Size of the header of the response.
         */
        private const val HEADER_RESPONSE_SIZE = Byte.SIZE_BYTES + Short.SIZE_BYTES + Byte.SIZE_BYTES + Int.SIZE_BYTES

        /**
         * Size of the version handshake packet.
         */
        private const val HANSHAKE_PACKET_SIZE = Int.SIZE_BYTES + Byte.SIZE_BYTES

        /**
         * Amount of bytes to read after decoding the header.
         */
        private const val BYTES_AFTER_HEADER = Sector.DATA_SIZE - 8

        /**
         * Amount of bytes to read per block.
         */
        private const val BYTES_AFTER_BLOCK = Sector.DATA_SIZE - 1

        /**
         * Opens a [Js5NetReadStore] and initializes the JS5 connection.
         *
         * @param sockAddr The address to connect to.
         * @param revision The current game revision to connect to.
         * @param xorKey The encryption key to use.
         * @param priorityMode Whether to use [Js5NetReadStore.priorityMode]
         */
        public fun open(
            sockAddr: InetSocketAddress,
            revision: Int,
            xorKey: Byte = 0,
            priorityMode: Boolean = false
        ): Js5NetReadStore {
            logger.info("Initializing JS5 connection to ${sockAddr.address}")
            val socketChannel = SocketChannel.open(sockAddr).apply {
                setOption(StandardSocketOptions.TCP_NODELAY, true)
                setOption(StandardSocketOptions.SO_KEEPALIVE, true)
            }
            logger.info("Successfully connected to ${sockAddr.address}")
            logger.info("Sending version handshake for revision $revision")
            val hsBuf = Unpooled.buffer(HANSHAKE_PACKET_SIZE).apply {
                writeByte(JS5_CONNECTION_TYPE)
                writeInt(revision)
            }
            hsBuf.readBytes(socketChannel, hsBuf.readableBytes())
            val statusCode = Unpooled.buffer(Byte.SIZE_BYTES).apply {
                writeBytes(socketChannel, writableBytes())
            }.readUnsignedByte().toInt()
            if (statusCode != 0) throw IOException(
                "Could not establish connection with JS5 Server status code: $statusCode."
            )
            logger.info("JS5 connection successfully established")
            val js5SocketReader = Js5NetReadStore(socketChannel, priorityMode)
            if (xorKey.toInt() != 0) js5SocketReader.updateEncryptionKey(xorKey)
            return js5SocketReader
        }

    }
}
