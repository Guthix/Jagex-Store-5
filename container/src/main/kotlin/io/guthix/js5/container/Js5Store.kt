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

import io.netty.buffer.ByteBuf

/** [Js5Container] reader and writer. */
public interface Js5Store : Js5ReadStore, Js5WriteStore {
    public companion object {
        /**
         * The master index file id that contains the settings.
         */
        public const val MASTER_INDEX: Int = 255
    }
}

/** [Js5Container] reader. */
public interface Js5ReadStore : AutoCloseable {
    public fun read(indexId: Int, containerId: Int): ByteBuf
}

/** [Js5Container] writer. */
public interface Js5WriteStore : AutoCloseable {
    public var archiveCount: Int

    public fun write(indexId: Int, containerId: Int, data: ByteBuf)

    public fun remove(indexId: Int, containerId: Int)
}