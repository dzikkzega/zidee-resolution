package com.zidee.ress.engine.photo

import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.ScalingAlgorithm
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Composites `BLUR_BG_9_16`: scale-to-fill blurred backdrop (default radius 35px, darkened 12%)
 * with the sharp Lanczos-3 foreground centred on top.
 */
object BlurBackgroundCompositor {
    private const val BACKDROP_BRIGHTNESS = 0.88 // 12% darker per MEDIA_ENGINE_SPEC
    private const val BLUR_PROXY_DIVISOR = 4
    private const val OPAQUE_ALPHA_MASK = 0xFF shl 24

    fun compose(
        source: PixelBuffer,
        canvasSize: PixelSize,
        foregroundRect: PixelRect,
        algorithm: ScalingAlgorithm,
        blurRadiusPx: Int = CanvasGeometry.DEFAULT_BLUR_RADIUS_PX,
    ): PixelBuffer {
        require(
            foregroundRect.left >= 0 &&
                foregroundRect.top >= 0 &&
                foregroundRect.left + foregroundRect.width <= canvasSize.width &&
                foregroundRect.top + foregroundRect.height <= canvasSize.height,
        ) {
            "Foreground $foregroundRect exceeds canvas $canvasSize"
        }

        val clampedRadius = blurRadiusPx.coerceIn(CanvasGeometry.BLUR_RADIUS_RANGE)
        val backdrop = renderBlurredBackdrop(source, canvasSize, clampedRadius.toDouble())
        val foreground = LanczosResampler.resample(source, foregroundRect.size, algorithm)

        val out = backdrop.pixels.copyOf()
        for (y in 0 until foreground.height) {
            val dstRow = (foregroundRect.top + y) * canvasSize.width + foregroundRect.left
            val srcRow = y * foreground.width
            for (x in 0 until foreground.width) {
                val dstIdx = dstRow + x
                val fg = foreground.pixels[srcRow + x]
                val fgAlpha = (fg ushr 24) and 0xFF
                out[dstIdx] =
                    when (fgAlpha) {
                        255 -> fg
                        0 -> out[dstIdx] or OPAQUE_ALPHA_MASK
                        else -> alphaBlendOpaque(fg = fg, bg = out[dstIdx], alpha = fgAlpha)
                    }
            }
        }
        return PixelBuffer(canvasSize.width, canvasSize.height, out)
    }

    private fun alphaBlendOpaque(
        fg: Int,
        bg: Int,
        alpha: Int,
    ): Int {
        val invAlpha = 255 - alpha
        val outR = (((fg ushr 16) and 0xFF) * alpha + ((bg ushr 16) and 0xFF) * invAlpha + 127) / 255
        val outG = (((fg ushr 8) and 0xFF) * alpha + ((bg ushr 8) and 0xFF) * invAlpha + 127) / 255
        val outB = ((fg and 0xFF) * alpha + (bg and 0xFF) * invAlpha + 127) / 255
        return ColorMath.packArgb(255, outR, outG, outB)
    }

    private fun renderBlurredBackdrop(
        source: PixelBuffer,
        canvasSize: PixelSize,
        blurRadiusPx: Double,
    ): PixelBuffer {
        // Scale-to-fill crop from source so backdrop matches canvas aspect without distortion.
        val srcAspect = source.width.toDouble() / source.height
        val dstAspect = canvasSize.width.toDouble() / canvasSize.height
        val cropRegion =
            if (srcAspect > dstAspect) {
                val cropW = (source.height * dstAspect).roundToInt().coerceIn(1, source.width)
                PixelRect(left = (source.width - cropW) / 2, top = 0, width = cropW, height = source.height)
            } else {
                val cropH = (source.width / dstAspect).roundToInt().coerceIn(1, source.height)
                PixelRect(left = 0, top = (source.height - cropH) / 2, width = source.width, height = cropH)
            }
        val cropped = LanczosResampler.crop(source, cropRegion)

        // Downsample 4x before Gaussian blur: visually identical at r=35, ~16x faster on CPU.
        val proxyW = max(16, canvasSize.width / BLUR_PROXY_DIVISOR)
        val proxyH = max(16, canvasSize.height / BLUR_PROXY_DIVISOR)
        val proxy = LanczosResampler.resample(cropped, PixelSize(proxyW, proxyH), ScalingAlgorithm.BICUBIC)
        val scaledSigma = (blurRadiusPx * proxyW.toDouble() / canvasSize.width).coerceAtLeast(1.0)
        val blurredProxy = UnsharpMaskFilter.gaussianBlur(proxy, scaledSigma, BACKDROP_BRIGHTNESS)
        return LanczosResampler.resample(blurredProxy, canvasSize, ScalingAlgorithm.BICUBIC)
    }
}
