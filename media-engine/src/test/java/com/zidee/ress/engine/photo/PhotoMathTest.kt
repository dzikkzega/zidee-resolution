package com.zidee.ress.engine.photo

import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.ScalingAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhotoMathTest {
    @Test
    fun `lanczos3 and bicubic downscale to exact target dimensions`() {
        val src = solidBuffer(400, 300, 0xFF336699.toInt())

        val lanczos = LanczosResampler.resample(src, PixelSize(108, 82), ScalingAlgorithm.LANCZOS3)
        val bicubic = LanczosResampler.resample(src, PixelSize(108, 82), ScalingAlgorithm.BICUBIC)

        assertEquals(108, lanczos.width)
        assertEquals(82, lanczos.height)
        assertEquals(108, bicubic.width)
        assertEquals(82, bicubic.height)
    }

    @Test
    fun `resampler preserves solid colour across every pixel`() {
        val color = 0xFF8040C0.toInt()
        val src = solidBuffer(64, 48, color)

        for (algo in ScalingAlgorithm.entries) {
            val out = LanczosResampler.resample(src, PixelSize(32, 24), algo)
            for (pixel in out.pixels) {
                assertEquals("colour drift for $algo", color, pixel)
            }
        }
    }

    @Test
    fun `lanczos3 produces steeper edge contrast than bicubic on downscaled step edge`() {
        val width = 120
        val height = 40
        val pixels =
            IntArray(width * height) { idx ->
                val x = idx % width
                if (x < width / 2) 0xFF101010.toInt() else 0xFFF0F0F0.toInt()
            }
        val src = PixelBuffer(width, height, pixels)

        val lanczos = LanczosResampler.resample(src, PixelSize(40, 14), ScalingAlgorithm.LANCZOS3)
        val bicubic = LanczosResampler.resample(src, PixelSize(40, 14), ScalingAlgorithm.BICUBIC)

        assertTrue(
            "Lanczos edge variance should be >= Bicubic",
            edgeGradientEnergy(lanczos) >= edgeGradientEnergy(bicubic),
        )
    }

    @Test
    fun `crop extracts exact region before resampling`() {
        val width = 100
        val height = 100
        val pixels =
            IntArray(width * height) { idx ->
                val x = idx % width
                val y = idx / width
                if (x in 20 until 60 && y in 10 until 90) 0xFF00FF00.toInt() else 0xFFFF0000.toInt()
            }
        val src = PixelBuffer(width, height, pixels)

        val cropped = LanczosResampler.crop(src, PixelRect(20, 10, 40, 80))

        assertEquals(40, cropped.width)
        assertEquals(80, cropped.height)
        assertTrue(cropped.pixels.all { it == 0xFF00FF00.toInt() })
    }

    @Test
    fun `unsharp mask with zero alpha leaves pixels identical`() {
        val src = checkerboard(32, 32, 0xFF202020.toInt(), 0xFFD0D0D0.toInt())

        val out = UnsharpMaskFilter.apply(src, amount = 0.0)

        assertTrue(src.pixels.contentEquals(out.pixels))
    }

    @Test
    fun `unsharp mask increases edge variance without altering flat regions below threshold`() {
        val width = 48
        val height = 48
        val pixels =
            IntArray(width * height) { idx ->
                val x = idx % width
                when {
                    x < 16 -> 0xFF808080.toInt()
                    x < 32 -> if ((x % 2) == 0) 0xFF404040.toInt() else 0xFFC0C0C0.toInt()
                    else -> if ((idx % 2) == 0) 0xFF808080.toInt() else 0xFF818181.toInt() // diff 1 < threshold 4
                }
            }
        val src = PixelBuffer(width, height, pixels)

        val sharpened = UnsharpMaskFilter.apply(src, amount = 0.375)

        assertTrue(
            "Edge variance should increase after USM",
            edgeGradientEnergy(sharpened) > edgeGradientEnergy(src),
        )
        // Flat region on the far right (diff <= 1 < T=4) stays untouched
        for (y in 4 until height - 4) {
            for (x in 36 until width - 4) {
                val i = y * width + x
                assertEquals("Flat low-contrast pixel ($x,$y) altered", src.pixels[i], sharpened.pixels[i])
            }
        }
    }

    @Test
    fun `blur background compositor fills 9 by 16 canvas with darkened blur and centered sharp foreground`() {
        val canvas = PixelSize(108, 192)
        val fgRect = PixelRect(0, 66, 108, 60)
        val src = solidBuffer(160, 90, 0xFFFFFFFF.toInt())

        val result =
            BlurBackgroundCompositor.compose(
                source = src,
                canvasSize = canvas,
                foregroundRect = fgRect,
                algorithm = ScalingAlgorithm.LANCZOS3,
            )

        assertEquals(108, result.width)
        assertEquals(192, result.height)
        // Top backdrop row is darkened by 12% (~224 vs 255)
        val bgRed = (result.pixels[5 * 108 + 54] ushr 16) and 0xFF
        assertTrue("Backdrop red $bgRed should be darkened around 224", abs(bgRed - 224) <= 6)
        // Foreground centre remains crisp white 255
        val fgRed = (result.pixels[96 * 108 + 54] ushr 16) and 0xFF
        assertEquals(255, fgRed)
    }

    @Test
    fun `blur background compositor respects custom blur radius across 10 to 60 px`() {
        val canvas = PixelSize(108, 192)
        val fgRect = PixelRect(0, 66, 108, 60)
        val stepSrc =
            PixelBuffer(
                160,
                90,
                IntArray(160 * 90) { idx ->
                    val x = idx % 160
                    if (x < 80) 0xFF101010.toInt() else 0xFFF0F0F0.toInt()
                },
            )

        val soft =
            BlurBackgroundCompositor.compose(
                source = stepSrc,
                canvasSize = canvas,
                foregroundRect = fgRect,
                algorithm = ScalingAlgorithm.LANCZOS3,
                blurRadiusPx = 15,
            )
        val strong =
            BlurBackgroundCompositor.compose(
                source = stepSrc,
                canvasSize = canvas,
                foregroundRect = fgRect,
                algorithm = ScalingAlgorithm.LANCZOS3,
                blurRadiusPx = 55,
            )

        assertEquals(108, soft.width)
        assertEquals(192, strong.height)
        assertTrue(soft.pixels.all { ((it ushr 24) and 0xFF) == 255 })
        assertTrue(strong.pixels.all { ((it ushr 24) and 0xFF) == 255 })
        assertFalse("Stronger blur radius should yield a different backdrop transition", soft.pixels.contentEquals(strong.pixels))
    }

    @Test
    fun `blur background compositor alpha blends transparent foreground over opaque backdrop`() {
        val canvas = PixelSize(108, 192)
        val fgRect = PixelRect(0, 66, 108, 60)
        // Top half of source is solid white (for backdrop fill & foreground), bottom row is transparent black.
        val src =
            PixelBuffer(
                108,
                60,
                IntArray(108 * 60) { idx ->
                    val y = idx / 108
                    when {
                        y < 30 -> 0xFFFFFFFF.toInt()
                        y < 45 -> 0x800000FF.toInt() // 50% translucent blue
                        else -> 0x00000000 // fully transparent black
                    }
                },
            )

        val result =
            BlurBackgroundCompositor.compose(
                source = src,
                canvasSize = canvas,
                foregroundRect = fgRect,
                algorithm = ScalingAlgorithm.LANCZOS3,
            )

        val translucentCenter = result.pixels[(66 + 37) * 108 + 54]
        val transparentCenter = result.pixels[(66 + 55) * 108 + 54]

        // Output pixels must be opaque for JPEG encoding (alpha == 255)
        assertEquals(255, (translucentCenter ushr 24) and 0xFF)
        assertEquals(255, (transparentCenter ushr 24) and 0xFF)
        // Transparent region must show darkened blurred backdrop, never 0x000000 black
        val transparentBlue = transparentCenter and 0xFF
        assertTrue("Expected backdrop color instead of black, blue=$transparentBlue", transparentBlue > 40)
        // Translucent blue must keep strong blue component blended over backdrop
        val translucentBlue = translucentCenter and 0xFF
        assertTrue("Expected blended blue > 120, was $translucentBlue", translucentBlue > 120)
    }

    private fun solidBuffer(
        w: Int,
        h: Int,
        argb: Int,
    ) = PixelBuffer(w, h, IntArray(w * h) { argb })

    private fun checkerboard(
        w: Int,
        h: Int,
        a: Int,
        b: Int,
    ) = PixelBuffer(
        w,
        h,
        IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if ((x + y) % 2 == 0) a else b
        },
    )

    private fun edgeGradientEnergy(buffer: PixelBuffer): Long {
        var energy = 0L
        for (y in 1 until buffer.height - 1) {
            for (x in 1 until buffer.width - 1) {
                val c = luminance(buffer.pixels[y * buffer.width + x])
                val r = luminance(buffer.pixels[y * buffer.width + x + 1])
                val d = luminance(buffer.pixels[(y + 1) * buffer.width + x])
                energy += abs(c - r) + abs(c - d)
            }
        }
        return energy
    }

    private fun luminance(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}
