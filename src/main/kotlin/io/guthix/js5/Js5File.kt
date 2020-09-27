/*
 * Copyright 2018-2020 Guthix
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
package io.guthix.js5

import io.netty.buffer.ByteBuf
import io.netty.buffer.DefaultByteBufHolder

/**
 * The smallest data unit in a [Js5Cache]. Each file contains data and optionally has a [nameHash]. A [Js5File] is
 * always part of a [Js5Group].
 *
 * @property id The unique identifier in the group of this [Js5File].
 * @property nameHash (Optional) The unique string identifier stored as a [String.hashCode].
 * @property data The domain data of the file.
 */
public data class Js5File(var id: Int, var nameHash: Int?, var data: ByteBuf) : DefaultByteBufHolder(data)

/**
 * The settings for a [Js5File].
 *
 * @property id The unique identifier in the group of this [Js5File].
 * @property nameHash (Optional) The unique string identifier in the group stored as a [String.hashCode].
 */
public data class Js5FileSettings(val id: Int, val nameHash: Int?)