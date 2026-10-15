package com.finisdiscago.leximvp.core.document

internal fun imageSampleSize(width: Int, height: Int): Int {
    require(width > 0 && height > 0)
    var sample = 1
    while (maxOf(width, height) / sample > 2400) sample *= 2
    return sample
}

internal fun ocrBitmapSize(width: Int, height: Int): Pair<Int, Int> {
    require(width > 0 && height > 0)
    val scale = minOf(3f, 2400f / maxOf(width, height))
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}
