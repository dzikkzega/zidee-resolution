package com.zidee.ress.engine.photo

import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.ScalingAlgorithm
import com.zidee.ress.engine.photo.ColorMath.clampByte
import com.zidee.ress.engine.photo.ColorMath.packArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Separable 2-pass image resampler supporting Lanczos-3 (`a = 3`) and Catmull-Rom Bicubic (`a = 2`).
 */
object LanczosResampler {
    fun crop(
        source: PixelBuffer,
        region: PixelRect,
    ): PixelBuffer {
        require(region.left + region.width <= source.width && region.top + region.height <= source.height) {
            "Crop $region exceeds source ${source.width}x${source.height}"
        }
        val out = IntArray(region.width * region.height)
        for (y in 0 until region.height) {
            val srcOffset = (region.top + y) * source.width + region.left
            val dstOffset = y * region.width
            source.pixels.copyInto(out, dstOffset, srcOffset, srcOffset + region.width)
        }
        return PixelBuffer(region.width, region.height, out)
    }

    fun resample(
        source: PixelBuffer,
        target: PixelSize,
        algorithm: ScalingAlgorithm = ScalingAlgorithm.LANCZOS3,
    ): PixelBuffer {
        if (source.width == target.width && source.height == target.height) {
            return PixelBuffer(target.width, target.height, source.pixels.copyOf())
        }

        val radius =
            when (algorithm) {
                ScalingAlgorithm.LANCZOS3 -> 3.0
                ScalingAlgorithm.BICUBIC -> 2.0
            }
        val kernel: (Double) -> Double =
            when (algorithm) {
                ScalingAlgorithm.LANCZOS3 -> ::lanczos3Kernel
                ScalingAlgorithm.BICUBIC -> ::catmullRomKernel
            }

        val horizontal = resampleHorizontal(source, target.width, radius, kernel)
        return resampleVertical(horizontal, target.height, radius, kernel)
    }

    private fun resampleHorizontal(
        src: PixelBuffer,
        dstWidth: Int,
        radius: Double,
        kernel: (Double) -> Double,
    ): PixelBuffer {
        val ratio = src.width.toDouble() / dstWidth
        val supportScale = max(1.0, ratio)
        val halfWindow = radius * supportScale
        val table = buildWeights(dstWidth, ratio, supportScale, halfWindow, kernel)

        val out = IntArray(dstWidth * src.height)
        for (y in 0 until src.height) {
            val srcRow = y * src.width
            val dstRow = y * dstWidth
            for (dx in 0 until dstWidth) {
                val start = table.starts[dx]
                val count = table.counts[dx]
                val wOffset = table.weightOffsets[dx]

                var sumA = 0.0
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0
                for (k in 0 until count) {
                    val w = table.weights[wOffset + k]
                    val sx = (start + k).coerceIn(0, src.width - 1)
                    val p = src.pixels[srcRow + sx]
                    sumA += ((p ushr 24) and 0xFF) * w
                    sumR += ((p ushr 16) and 0xFF) * w
                    sumG += ((p ushr 8) and 0xFF) * w
                    sumB += (p and 0xFF) * w
                }
                out[dstRow + dx] = packArgb(clampByte(sumA), clampByte(sumR), clampByte(sumG), clampByte(sumB))
            }
        }
        return PixelBuffer(dstWidth, src.height, out)
    }

    private fun resampleVertical(
        src: PixelBuffer,
        dstHeight: Int,
        radius: Double,
        kernel: (Double) -> Double,
    ): PixelBuffer {
        val ratio = src.height.toDouble() / dstHeight
        val supportScale = max(1.0, ratio)
        val halfWindow = radius * supportScale
        val table = buildWeights(dstHeight, ratio, supportScale, halfWindow, kernel)

        val out = IntArray(src.width * dstHeight)
        for (dy in 0 until dstHeight) {
            val start = table.starts[dy]
            val count = table.counts[dy]
            val wOffset = table.weightOffsets[dy]
            val dstRow = dy * src.width

            for (x in 0 until src.width) {
                var sumA = 0.0
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0
                for (k in 0 until count) {
                    val w = table.weights[wOffset + k]
                    val sy = (start + k).coerceIn(0, src.height - 1)
                    val p = src.pixels[sy * src.width + x]
                    sumA += ((p ushr 24) and 0xFF) * w
                    sumR += ((p ushr 16) and 0xFF) * w
                    sumG += ((p ushr 8) and 0xFF) * w
                    sumB += (p and 0xFF) * w
                }
                out[dstRow + x] = packArgb(clampByte(sumA), clampByte(sumR), clampByte(sumG), clampByte(sumB))
            }
        }
        return PixelBuffer(src.width, dstHeight, out)
    }

    private class WeightTable(
        val starts: IntArray,
        val counts: IntArray,
        val weightOffsets: IntArray,
        val weights: DoubleArray,
    )

    private fun buildWeights(
        dstLen: Int,
        ratio: Double,
        supportScale: Double,
        halfWindow: Double,
        kernel: (Double) -> Double,
    ): WeightTable {
        val maxTaps = ceil(halfWindow * 2.0).toInt() + 3
        val starts = IntArray(dstLen)
        val counts = IntArray(dstLen)
        val offsets = IntArray(dstLen)
        val weights = DoubleArray(dstLen * maxTaps)
        var cursor = 0

        for (i in 0 until dstLen) {
            val center = (i + 0.5) * ratio - 0.5
            val left = floor(center - halfWindow).toInt()
            val right = ceil(center + halfWindow).toInt()
            val count = (right - left + 1).coerceAtLeast(1)

            starts[i] = left
            counts[i] = count
            offsets[i] = cursor

            var sum = 0.0
            for (k in 0 until count) {
                val sample = left + k
                val w = kernel((sample - center) / supportScale)
                weights[cursor + k] = w
                sum += w
            }
            if (sum != 0.0) {
                val inv = 1.0 / sum
                for (k in 0 until count) {
                    weights[cursor + k] *= inv
                }
            } else {
                weights[cursor] = 1.0
            }
            cursor += count
        }
        return WeightTable(starts, counts, offsets, weights)
    }

    private fun lanczos3Kernel(x: Double): Double {
        val ax = abs(x)
        if (ax < 1e-9) return 1.0
        if (ax >= 3.0) return 0.0
        val piX = PI * ax
        val piX3 = piX / 3.0
        return (sin(piX) / piX) * (sin(piX3) / piX3)
    }

    private fun catmullRomKernel(x: Double): Double {
        val ax = abs(x)
        return when {
            ax < 1.0 -> 1.5 * ax * ax * ax - 2.5 * ax * ax + 1.0
            ax < 2.0 -> -0.5 * ax * ax * ax + 2.5 * ax * ax - 4.0 * ax + 2.0
            else -> 0.0
        }
    }
}
