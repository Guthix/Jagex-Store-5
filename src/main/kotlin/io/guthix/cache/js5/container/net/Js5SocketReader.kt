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

import io.guthix.cache.js5.container.Container
import io.guthix.cache.js5.container.ContainerReader
import io.guthix.cache.js5.container.filesystem.Segment
import io.guthix.cache.js5.io.uByte
import io.guthix.cache.js5.io.uInt
import io.guthix.cache.js5.io.uShort
import io.guthix.cache.js5.util.Js5Compression
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

internal enum class Js5Request(val opcode: Int) {
    NORMAL_FILE_REQUEST(0),
    PRIORITY_FILE_REQUEST(1),
    CLIENT_LOGGED_IN(2),
    CLIENT_LOGGED_OUT(3),
    ENCRYPTION_KEY_UPDATE(4);
}

/** Do not use! This reader has not been tested and is not fully implemented yet. */
@ExperimentalUnsignedTypes
class Js5SocketReader constructor(
    private val socketChannel: SocketChannel,
    private var xorKey: UByte = 0u,
    var priorityMode: Boolean = false,
    override val archiveCount: Int
) : ContainerReader {
    init {
        socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
        if(xorKey.toInt() != 0) {
            updateEncryptionKey(xorKey)
        }
    }

    override fun read(indexFileId: Int, containerId: Int): ByteBuffer {
        sendFileRequest(indexFileId, containerId, priorityMode)
        return readFileResponse()
    }

    private fun readFileResponse(): ByteBuffer {
        val headerBuffer = ByteBuffer.allocate(8)
        while(headerBuffer.remaining() > 0) {
            socketChannel.read(headerBuffer)
        }
        headerBuffer.flip()
        headerBuffer.uByte // index file id
        headerBuffer.uShort // group id
        val compression = Js5Compression.getByOpcode(headerBuffer.uByte.toInt())
        val compressedSize = headerBuffer.uInt

        // Create container and add meta-data
        val containerBuffer = ByteBuffer.allocate(Container.ENC_HEADER_SIZE + compression.headerSize + compressedSize)
        containerBuffer.put(compression.opcode.toByte())
        containerBuffer.putInt(compressedSize)

        // Read response data
        val dataResponseBuffer = ByteBuffer.allocate(
            compression.headerSize + compressedSize + Math.ceil(
                (compressedSize - BYTES_AFTER_HEADER) / BYTES_AFTER_BLOCK.toDouble()
            ).toInt()
        )
        while(dataResponseBuffer.remaining() > 0) {
            socketChannel.read(dataResponseBuffer)
        }
        dataResponseBuffer.flip()

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
            containerBuffer.put(dataResponseBuffer.array().sliceArray(start until start + blockDataSize))
            dataResponseBuffer.position(start + blockDataSize)
            i++
        }
        return containerBuffer.flip()
    }

    fun updateEncryptionKey(key: UByte) {
        xorKey = key
        sendEncryptionKeyChange(key)
    }

    private fun sendEncryptionKeyChange(key: UByte) {
        val buffer =ByteBuffer.allocate(REQUEST_PACKET_SIZE)
        buffer.put(Js5Request.ENCRYPTION_KEY_UPDATE.opcode.toByte())
        buffer.put(key.toByte())
        buffer.putShort(0)
        buffer.flip()
        socketChannel.write(buffer)
    }

    private fun sendFileRequest(indexFileId: Int, containerId: Int, priority: Boolean) {
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
        const val REQUEST_PACKET_SIZE = 4
        private const val BYTES_AFTER_HEADER = Segment.DATA_SIZE - 8
        private const val BYTES_AFTER_BLOCK = Segment.DATA_SIZE - 1

    }
}