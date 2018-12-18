/*
   Copyright 2018 Bart van Helvert

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package io.github.bartvhelvert.jagex.filesystem

import java.nio.ByteBuffer

@ExperimentalUnsignedTypes
fun ByteBuffer.getUByte() = get().toUByte()

@ExperimentalUnsignedTypes
fun ByteBuffer.getUShort() = short.toUShort()

@ExperimentalUnsignedTypes
fun ByteBuffer.getUMedium() = (short.toUShort().toInt() shl 8) or get().toUByte().toInt()

@ExperimentalUnsignedTypes
fun ByteBuffer.getMedium() = (short.toInt() shl 8) or get().toUByte().toInt()

fun ByteBuffer.putMedium(value: Int): ByteBuffer {
    require(value <= 16777215)
    putShort((value shr 8).toShort())
    put(value.toByte())
    return this
}