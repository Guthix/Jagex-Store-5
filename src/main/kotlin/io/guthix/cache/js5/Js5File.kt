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
package io.guthix.cache.js5

import io.netty.buffer.ByteBuf
import io.netty.buffer.DefaultByteBufHolder

/**
 * The smallest data unit in a [Js5Cache]. Each file contains data and optionally has a [nameHash]. A [Js5File] is
 * always part of a [Js5Group].
 *
 * @property nameHash (Optional) The unique string identifier stored as a [String.hashCode].
 * @property data The domain data of the file.
 */
data class Js5File(val id: Int, val nameHash: Int?, val data: ByteBuf) : DefaultByteBufHolder(data)

/**
 * The settings for a [Js5File].
 *
 * @property id The unique identifier in the group of this [Js5File].
 * @property nameHash (Optional) The unique string identifier in the group stored as a [String.hashCode].
 */
data class Js5FileSettings(val id: Int, val nameHash: Int?)