/*
 * This file is part of Guthix Jagex-Store-5.
 *
 * Guthix Jagex-Store-5 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix Jagex-Store-5 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Guthix Jagex-Store-5. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.cache.js5.container

import io.netty.buffer.ByteBuf

/**
 * [Js5Container] reader and writer.
 */
public interface Js5Store : Js5ReadStore, Js5WriteStore {
    public companion object {
        /**
         * The master index file id that contains the settings.
         */
        public const val MASTER_INDEX: Int = 255
    }
}

/**
 * [Js5Container] reader.
 */
public interface Js5ReadStore : AutoCloseable {
    public fun read(indexId: Int, containerId: Int): ByteBuf
}

/**
 * [Js5Container] writer.
 */
public interface Js5WriteStore : AutoCloseable {
    public var archiveCount: Int

    public fun write(indexId: Int, containerId: Int, data: ByteBuf)

    public fun remove(indexId: Int, containerId: Int)
}