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

package io.guthix.cache.fs.io

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

fun FileChannel.readFully(buffer: ByteBuffer, ptr: Long) {
    var pointer = ptr
    while (buffer.remaining() > 0) {
        val read = read(buffer, pointer).toLong()
        if (read < -1) {
            throw EOFException()
        } else {
            pointer += read
        }
    }
}