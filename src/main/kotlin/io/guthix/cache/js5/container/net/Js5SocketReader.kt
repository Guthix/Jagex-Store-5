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

import io.guthix.cache.js5.container.ContainerReader
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
    var priorityMode: Boolean = false
) : ContainerReader {
    override val archiveCount: Int get() = TODO() // Need to find a better way than brute forcing master index requests

    init {
        socketChannel.configureBlocking(true)
        if(xorKey.toInt() != 0) {
            updateEncryptionKey(xorKey)
        }
    }

    override fun read(indexFileId: Int, containerId: Int): ByteBuffer {
        sendFileRequest(indexFileId, containerId, priorityMode)
        val buffer = ByteBuffer.allocate(1024)
        socketChannel.read(buffer)
        return buffer
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
        socketChannel.write(buffer)
    }

    override fun close() {
        socketChannel.close()
    }

    companion object {
        const val REQUEST_PACKET_SIZE = 4
    }
}