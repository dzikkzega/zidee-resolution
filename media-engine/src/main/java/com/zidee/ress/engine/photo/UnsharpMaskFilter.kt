package com.zidee.ress.engine.photo

import com.zidee.ress.engine.photo.ColorMath.clampByte
import com.zidee.ress.engine.photo.ColorMath.luminance
import com.zidee.ress.engine.photo.ColorMath.packArgb
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp

/**
 * Smart Unsharp Mask (Anti-Blur Compensation) per MEDIA_ENGINE_SPEC §2:
 * - Gaussian blur sigma = 1.2 px
 * - Luminance threshold T = 4 (keeps smooth sky/skin noise-free)
 * - Strength alpha in 0.0..1.5
 */
object UnsharpMaskFilter {
    private const val DEFAULT_SIGMA = 1.2
    private const val DEFAULT_THRESHOLD = 4.0

    fun apply(
        source: PixelBuffer,
        amount: Double,
        sigma: Double = DEFAULT_SIGMA,
        threshold: Double = DEFAULT_THRESHOLD,
    ): PixelBuffer {
        if (amount <= 0.0) return PixelBuffer(source.width, source.height, source.pixels.copyOf())

        val blurred = gaussianBlur(source, sigma)
        val out = IntArray(source.pixels.size)
        for (i in source.pixels.indices) {
            val orig = source.pixels[i]
            val blur = blurred.pixels[i]
            if (abs(luminance(orig) - luminance(blur)) < threshold) {
                out[i] = orig
                continue
            }
            val a = (orig ushr 24) and 0xFF
            val r = clampByte(((orig ushr 16) and 0xFF) + amount * (((orig ushr 16) and 0xFF) - ((blur ushr 16) and 0xFF)))
            val g = clampByte(((orig ushr 8) and 0xFF) + amount * (((orig ushr 8) and 0xFF) - ((blur ushr 8) and 0xFF)))
            val b = clampByte((orig and 0xFF) + amount * ((orig and 0xFF) - (blur and 0xFF)))
            out[i] = packArgb(a, r, g, b)
        }
        return PixelBuffer(source.width, source.height, out)
    }

    internal fun gaussianBlur(
        source: PixelBuffer,
        sigma: Double,
        brightnessScale: Double = 1.0,
    ): PixelBuffer {
        if (sigma <= 0.0) {
            return if (brightnessScale == 1.0) {
                PixelBuffer(source.width, source.height, source.pixels.copyOf())
            } else {
                scaleBrightness(source, brightnessScale)
            }
        }
        val radius = ceil(sigma * 3.0).toInt().coerceAtLeast(1)
        val kernel = buildGaussianKernel(radius, sigma)
        val w = source.width
        val h = source.height

        // FloatArray cuts intermediate scratch heap in half (~24 MB vs ~48 MB at 1080x1920).
        val temp = FloatArray(w * h * 3)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in -radius..radius) {
                    val sx = (x + k).coerceIn(0, w - 1)
                    val p = source.pixels[row + sx]
                    val weight = kernel[k + radius]
                    r += ((p ushr 16) and 0xFF) * weight
                    g += ((p ushr 8) and 0xFF) * weight
                    b += (p and 0xFF) * weight
                }
                val base = (row + x) * 3
                temp[base] = r
                temp[base + 1] = g
                temp[base + 2] = b
            }
        }

        val scale = brightnessScale.toFloat()
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in -radius..radius) {
                    val sy = (y + k).coerceIn(0, h - 1)
                    val base = (sy * w + x) * 3
                    val weight = kernel[k + radius]
                    r += temp[base] * weight
                    g += temp[base + 1] * weight
                    b += temp[base + 2] * weight
                }
                val a = (source.pixels[y * w + x] ushr 24) and 0xFF
                out[y * w + x] =
                    packArgb(
                        a,
                        clampByte((r * scale).toDouble()),
                        clampByte((g * scale).toDouble()),
                        clampByte((b * scale).toDouble()),
                    )
            }
        }
        return PixelBuffer(w, h, out)
    }

    private fun scaleBrightness(
        source: PixelBuffer,
        scale: Double,
    ): PixelBuffer {
        val out =
            IntArray(source.pixels.size) { idx ->
                val p = source.pixels[idx]
                packArgb(
                    (p ushr 24) and 0xFF,
                    clampByte(((p ushr 16) and 0xFF) * scale),
                    clampByte(((p ushr 8) and 0xFF) * scale),
                    clampByte((p and 0xFF) * scale),
                )
            }
        return PixelBuffer(source.width, source.height, out)
    }

    private fun buildGaussianKernel(
        radius: Int,
        sigma: Double,
    ): FloatArray {
        val kernel = FloatArray(radius * 2 + 1)
        val twoSigmaSq = 2.0 * sigma * sigma
        var sum = 0.0
        val raw = DoubleArray(kernel.size)
        for (k in -radius..radius) {
            val v = exp(-(k * k) / twoSigmaSq)
            raw[k + radius] = v
            sum += v
        }
        val inv = 1.0 / sum
        for (i in kernel.indices) {
            kernel[i] = (raw[i] * inv).toFloat()
        }
        return kernel
    }
}
