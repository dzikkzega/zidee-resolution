package com.zidee.ress.engine.photo

import kotlin.math.roundToInt

/** ARGB_8888 pixel buffer kept in plain Kotlin so resampling and filters run in JVM unit tests. */
class PixelBuffer(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "Buffer size must be positive, was ${width}x$height" }
        require(pixels.size == width * height) {
            "Expected ${width * height} pixels, got ${pixels.size}"
        }
    }
}

internal object ColorMath {
    fun clampByte(v: Double): Int = v.roundToInt().coerceIn(0, 255)

    fun clampByte(v: Int): Int = v.coerceIn(0, 255)

    fun packArgb(
        a: Int,
        r: Int,
        g: Int,
        b: Int,
    ): Int = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** ITU-R BT.601 luminance in 0..255. */
    fun luminance(argb: Int): Double {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
