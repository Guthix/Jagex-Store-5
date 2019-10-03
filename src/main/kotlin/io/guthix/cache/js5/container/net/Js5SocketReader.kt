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
package io.guthix.cache.js5.container.net

import io.guthix.cache.js5.container.Js5Container
import io.guthix.cache.js5.container.disk.Sector
import io.guthix.cache.js5.container.Js5Compression
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
const val JS5_CONNECTION_TYPE = 15

/**
 * A Js5 request to the server.
 */
internal enum class Js5Request(val opcode: Int) {
    NORMAL_FILE_REQUEST(0),
    PRIORITY_FILE_REQUEST(1),
    CLIENT_LOGGED_IN(2),
    CLIENT_LOGGED_OUT(3),
    ENCRYPTION_KEY_UPDATE(4);
}

/**
 * A file request response from the server.
 */
data class FileResponse(val indexFileId: Int, val containerId: Int, val data: ByteBuf) : DefaultByteBufHolder(data)

/**
 * A socket reader for reading [Js5Container]s.
 *
 * Theere are 2 types of file requests which can be send using the socket reader. Prioritised requests and normal
 * requests. Prioritised file requests are used by the client for requesting files that are required to render the game.
 * The server prioritizes priority file requests over normal file requests when responding to requests.
 *
 * @param sockAddr Ine address to connect to.
 * @param revision The current game version.
 * @property xorKey XOR encryption key.
 * @property priorityMode Whether to make priority file requests.
 * @property archiveCount The amount of archives on the server.
 */
class Js5SocketReader(
    private val socketChannel: SocketChannel,
    var priorityMode: Boolean = false,
    var archiveCount: Int
)  {
    private var xorKey: Byte = 0

    /**
     * Requests container data and blocks until the response arrives.
     *
     * @param indexFileId The index to request.
     * @param containerId the container to request.
     */
    fun read(indexFileId: Int, containerId: Int): ByteBuf {
        sendFileRequest(indexFileId, containerId, priorityMode)
        return readFileResponse().data
    }

    /**
     * Reads the file response and blocks until it has been completely read.
     */
    fun readFileResponse(): FileResponse {
        val headerBuffer = Unpooled.buffer(8)
        headerBuffer.readBytes(socketChannel, headerBuffer.readableBytes())
        headerBuffer.forEachByte {
            it xor xorKey
            true
        }
        val indexFileId = headerBuffer.readUnsignedByte().toInt()
        val containerId = headerBuffer.readUnsignedShort()
        logger.debug("Reading index file $indexFileId container $containerId")
        val compression = Js5Compression.getByOpcode(headerBuffer.readUnsignedByte().toInt())
        val compressedSize = headerBuffer.readInt()

        // Create container and add meta-data
        val containerBuffer = Unpooled.buffer(
            Js5Container.ENC_HEADER_SIZE + compression.headerSize + compressedSize
        )
        containerBuffer.writeByte(compression.opcode)
        containerBuffer.writeInt(compressedSize)

        // Read response data
        val containerSize = compression.headerSize + compressedSize
        val dataResponseBuffer = Unpooled.buffer(
            containerSize + ceil((containerSize - BYTES_AFTER_HEADER) / BYTES_AFTER_BLOCK.toDouble()).toInt()
        )
        dataResponseBuffer.readBytes(dataResponseBuffer, dataResponseBuffer.writableBytes())
        dataResponseBuffer.forEachByte {
            it xor xorKey
            true
        }

        // write all data after header
        val headerDataSize = if(dataResponseBuffer.readableBytes() < BYTES_AFTER_HEADER) {
            dataResponseBuffer.readableBytes()
        } else {
            BYTES_AFTER_HEADER
        }
        containerBuffer.writeBytes(dataResponseBuffer.slice(0, headerDataSize))

        // write other data
        var i = 0
        while(dataResponseBuffer.isReadable) {
            var start = BYTES_AFTER_HEADER + i * (Sector.DATA_SIZE)
            start += 1 //skip 255
            val blockBytesLeft = dataResponseBuffer.readableBytes() - start
            val blockDataSize = if(blockBytesLeft < BYTES_AFTER_BLOCK) {
                blockBytesLeft
            } else {
                BYTES_AFTER_BLOCK
            }
            dataResponseBuffer.writeBytes(dataResponseBuffer.slice(start, blockDataSize))
            dataResponseBuffer.readerIndex(start + blockDataSize)
            i++
        }
        return FileResponse(indexFileId, containerId, containerBuffer)
    }

    /**
     * Updates the XOR encryption key.
     *
     * @param key The key to update.
     */
    fun updateEncryptionKey(key: Byte) {
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
    fun sendFileRequest(indexFileId: Int, containerId: Int, priority: Boolean = priorityMode) {
        logger.debug("Requesting index file $indexFileId container $containerId")
        val buf = Unpooled.buffer(REQUEST_PACKET_SIZE)
        if(priority) {
            buf.writeByte(Js5Request.PRIORITY_FILE_REQUEST.opcode)
        } else {
            buf.writeByte(Js5Request.NORMAL_FILE_REQUEST.opcode)
        }
        buf.writeByte(indexFileId)
        buf.writeShort(containerId)
        buf.readBytes(socketChannel, buf.readableBytes())
    }

    fun close() {
        socketChannel.close()
    }

    companion object {
        /**
         * Request packet size for any request.
         */
        private const val REQUEST_PACKET_SIZE = 4

        /**
         * Amount of bytes to read after decoding the header.
         */
        private const val BYTES_AFTER_HEADER = Sector.DATA_SIZE - 8

        /**
         * Amount of bytes to read per block.
         */
        private const val BYTES_AFTER_BLOCK = Sector.DATA_SIZE - 1

        fun open(
            sockAddr: InetSocketAddress,
            revision: Int,
            xorKey: Byte = 0,
            priorityMode: Boolean = false,
            archiveCount: Int
        ): Js5SocketReader {
            val socketChannel = SocketChannel.open(sockAddr)
            logger.info("Initializing JS5 connection to ${sockAddr.address}")
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
            socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
            logger.info("Setting XOR encryption key to $xorKey")
            logger.info("Sending version handshake for revision $revision")
            val buffer = Unpooled.buffer(5).apply {
                writeByte(JS5_CONNECTION_TYPE)
                writeInt(revision)
            }
            buffer.readBytes(socketChannel, buffer.readableBytes())
            val buf = Unpooled.buffer(1)
            buf.readBytes(socketChannel, buf.readableBytes())
            val statusCode = buf.readUnsignedByte().toInt()
            if(statusCode != 0) throw IOException("Could not establish connection with JS5 Server error code $statusCode.")
            logger.info("JS5 connection successfully established")
            val js5SocketReader = Js5SocketReader(socketChannel, priorityMode, archiveCount)
            if(xorKey.toInt() != 0) js5SocketReader.updateEncryptionKey(xorKey)
            return js5SocketReader
        }

    }
}
