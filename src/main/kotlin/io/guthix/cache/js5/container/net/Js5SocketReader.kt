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
import io.guthix.cache.js5.container.Js5ContainerReader
import io.guthix.cache.js5.container.filesystem.Segment
import io.guthix.cache.js5.io.uByte
import io.guthix.cache.js5.io.uInt
import io.guthix.cache.js5.io.uShort
import io.guthix.cache.js5.util.Js5Compression
import mu.KotlinLogging
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.experimental.xor
import kotlin.math.ceil

private val logger = KotlinLogging.logger {}

/**
 * Connection type used for establishing a JS5 connection.
 */
const val JS5_CONNECTION_TYPE: Byte = 15

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
data class FileResponse(val indexFileId: Int, val containerId: Int, val data: ByteArray)

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
    sockAddr: InetSocketAddress,
    revision: Int,
    private var xorKey: Byte = 0,
    var priorityMode: Boolean = false,
    override val archiveCount: Int
) : Js5ContainerReader {
    private val socketChannel = SocketChannel.open(sockAddr)

    init {
        socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
        if(xorKey.toInt() != 0) {
            updateEncryptionKey(xorKey)
        }
        logger.info("Initializing JS5 connection to ${sockAddr.address} revision $revision")
        socketChannel.write(ByteBuffer.allocate(5).apply {
            put(JS5_CONNECTION_TYPE)
            putInt(revision)
        }.flip())
        val buffer = ByteBuffer.allocate(1)
        while(socketChannel.read(buffer) > 0) { // read response
            buffer.flip()
            val statusCode = buffer.uByte.toInt()
            if(statusCode != 0) throw IOException(
                "Could not establish connection withg JS5 Server error code $statusCode."
            )
        }
    }

    /**
     * Requests container data and blocks until the response arrives.
     *
     * @param indexFileId The index to request.
     * @param containerId the container to request.
     */
    override fun read(indexFileId: Int, containerId: Int): ByteArray {
        sendFileRequest(indexFileId, containerId, priorityMode)
        return readFileResponse().data
    }

    /**
     * Reads the file response and blocks until it has been completely read.
     */
    fun readFileResponse(): FileResponse {
        var headerBuffer = ByteBuffer.allocate(8)
        while(headerBuffer.remaining() > 0) {
            socketChannel.read(headerBuffer)
        }
        headerBuffer = ByteBuffer.wrap(headerBuffer.array().map {it xor xorKey}.toByteArray())
        val indexFileId = headerBuffer.uByte.toInt()
        val containerId = headerBuffer.uShort.toInt()
        logger.info("Reading index file $indexFileId container $containerId")
        val compression = Js5Compression.getByOpcode(headerBuffer.uByte.toInt())
        val compressedSize = headerBuffer.uInt

        // Create container and add meta-data
        val containerBuffer = ByteBuffer.allocate(
            Js5Container.ENC_HEADER_SIZE + compression.headerSize + compressedSize
        )
        containerBuffer.put(compression.opcode.toByte())
        containerBuffer.putInt(compressedSize)

        // Read response data
        val containerSize = compression.headerSize + compressedSize
        var dataResponseBuffer = ByteBuffer.allocate(
            containerSize + ceil((containerSize - BYTES_AFTER_HEADER) / BYTES_AFTER_BLOCK.toDouble()).toInt()
        )
        while(dataResponseBuffer.remaining() > 0) {
            socketChannel.read(dataResponseBuffer)
        }
        dataResponseBuffer = ByteBuffer.wrap(dataResponseBuffer.array().map {it xor xorKey}.toByteArray())

        // write all data after header
        val headerBytesLeft = dataResponseBuffer.limit() - dataResponseBuffer.position()
        val headerDataSize = if(headerBytesLeft < BYTES_AFTER_HEADER) {
            headerBytesLeft
        } else {
            BYTES_AFTER_HEADER
        }
        containerBuffer.put(dataResponseBuffer.array().sliceArray(0 until headerDataSize))

        // write other data
        var i = 0
        while(dataResponseBuffer.position() < dataResponseBuffer.limit()) {
            var start = BYTES_AFTER_HEADER + i * (Segment.DATA_SIZE)
            start += 1 //skip 255
            val blockBytesLeft = dataResponseBuffer.limit() - start
            val blockDataSize = if(blockBytesLeft < BYTES_AFTER_BLOCK) {
                blockBytesLeft
            } else {
                BYTES_AFTER_BLOCK
            }
            val data = dataResponseBuffer.array().sliceArray(start until start + blockDataSize)
            containerBuffer.put(data)
            dataResponseBuffer.position(start + blockDataSize)
            i++
        }
        return FileResponse(indexFileId, containerId, containerBuffer.array())
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
        val buffer =ByteBuffer.allocate(REQUEST_PACKET_SIZE)
        buffer.put(Js5Request.ENCRYPTION_KEY_UPDATE.opcode.toByte())
        buffer.put(key)
        buffer.putShort(0)
        buffer.flip()
        socketChannel.write(buffer)
    }

    /**
     * Sends a file request to the server.
     *
     * @param indexFileId The index id to request.
     * @param containerId The container id to request.
     * @param priority Whether to send a priority request.
     */
    fun sendFileRequest(indexFileId: Int, containerId: Int, priority: Boolean = priorityMode) {
        logger.info("Requesting index file $indexFileId container $containerId")
        val buffer = ByteBuffer.allocate(REQUEST_PACKET_SIZE)
        if(priority) {
            buffer.put(Js5Request.PRIORITY_FILE_REQUEST.opcode.toByte())
        } else {
            buffer.put(Js5Request.NORMAL_FILE_REQUEST.opcode.toByte())
        }
        buffer.put(indexFileId.toByte())
        buffer.putShort(containerId.toShort())
        buffer.flip()
        socketChannel.write(buffer)
    }

    override fun close() {
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
        private const val BYTES_AFTER_HEADER = Segment.DATA_SIZE - 8

        /**
         * Amount of bytes to read per block.
         */
        private const val BYTES_AFTER_BLOCK = Segment.DATA_SIZE - 1

    }
}
