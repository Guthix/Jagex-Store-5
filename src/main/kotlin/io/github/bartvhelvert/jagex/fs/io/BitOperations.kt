package io.github.bartvhelvert.jagex.fs.io

fun nextPowerOfTwo(value: Int): Int {
    var result = value
    --result
    result = result or result.ushr(1)
    result = result or result.ushr(2)
    result = result or result.ushr(4)
    result = result or result.ushr(8)
    result = result or result.ushr(16)
    return result + 1
}