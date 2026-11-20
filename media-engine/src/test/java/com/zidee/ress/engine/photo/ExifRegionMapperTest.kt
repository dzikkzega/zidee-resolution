package com.zidee.ress.engine.photo

import androidx.exifinterface.media.ExifInterface
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifRegionMapperTest {
    @Test
    fun `mapOrientedCropToRaw matches full orient then crop pixel-for-pixel for all 8 EXIF orientations`() {
        val rawSize = PixelSize(width = 60, height = 40)
        val rawPixels =
            IntArray(rawSize.width * rawSize.height) { idx ->
                val x = idx % rawSize.width
                val y = idx / rawSize.width
                (x shl 16) or y
            }
        val rawBuffer = PixelBuffer(rawSize.width, rawSize.height, rawPixels)

        val orientations =
            listOf(
                ExifInterface.ORIENTATION_NORMAL,
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_FLIP_VERTICAL,
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_ROTATE_270,
            )

        for (orientation in orientations) {
            val fullOriented = orientBuffer(rawBuffer, orientation)
            val uprightSize = ExifRegionMapper.orientedSize(rawSize, orientation)
            assertEquals("oriented width mismatch for $orientation", uprightSize.width, fullOriented.width)
            assertEquals("oriented height mismatch for $orientation", uprightSize.height, fullOriented.height)

            val orientedCrop = PixelRect(left = 7, top = 5, width = 18, height = 22)
            val reference = LanczosResampler.crop(fullOriented, orientedCrop)

            val rawCrop = ExifRegionMapper.mapOrientedCropToRaw(orientedCrop, rawSize, orientation)
            val rawSubBuffer = LanczosResampler.crop(rawBuffer, rawCrop)
            val regionThenOriented = orientBuffer(rawSubBuffer, orientation)

            assertEquals("cropped width mismatch for $orientation", reference.width, regionThenOriented.width)
            assertEquals("cropped height mismatch for $orientation", reference.height, regionThenOriented.height)
            assertArrayEquals(
                "region-decode + orient diverged from full orient + crop for EXIF=$orientation",
                reference.pixels,
                regionThenOriented.pixels,
            )
        }
    }

    @Test
    fun `computeBoundedSampleSize keeps 12000x9000 crop sharp while capping full-image fallback`() {
        // Region decode path: only decodes the 2160x3840 crop (8.29 MP <= 16 MP ceiling) -> sample=1
        val regionSample =
            ExifRegionMapper.computeBoundedSampleSize(
                decodeWidth = 2160,
                decodeHeight = 3840,
                targetWidth = 1080,
                targetHeight = 1920,
            )
        assertEquals(1, regionSample)
        val regionDecodedPixels = (2160L / regionSample) * (3840L / regionSample)
        assertTrue(regionDecodedPixels <= ExifRegionMapper.DEFAULT_MAX_DECODED_PIXELS)

        // Full-image fallback or Scale/Blur on 12000x9000 (108 MP): must stay strictly <= 16 MP ceiling
        val fullFallbackSample =
            ExifRegionMapper.computeBoundedSampleSize(
                decodeWidth = 12_000,
                decodeHeight = 9_000,
                targetWidth = 1080,
                targetHeight = 1920,
            )
        assertEquals(4, fullFallbackSample)
        val fallbackPixels = (12_000L / fullFallbackSample) * (9_000L / fullFallbackSample)
        assertTrue(
            "Fallback decoded pixels $fallbackPixels must be <= ${ExifRegionMapper.DEFAULT_MAX_DECODED_PIXELS}",
            fallbackPixels <= ExifRegionMapper.DEFAULT_MAX_DECODED_PIXELS,
        )
    }

    @Test
    fun `computeBoundedSampleSize enforces pixel ceiling even when target is large`() {
        val sample =
            ExifRegionMapper.computeBoundedSampleSize(
                decodeWidth = 12_000,
                decodeHeight = 9_000,
                targetWidth = 4096,
                targetHeight = 4096,
            )
        assertEquals(4, sample)
        val decodedPixels = (12_000L / sample) * (9_000L / sample)
        assertTrue(decodedPixels <= ExifRegionMapper.DEFAULT_MAX_DECODED_PIXELS)
    }

    private fun orientBuffer(
        src: PixelBuffer,
        orientation: Int,
    ): PixelBuffer {
        val rawW = src.width
        val rawH = src.height
        val dstSize = ExifRegionMapper.orientedSize(PixelSize(rawW, rawH), orientation)
        val out = IntArray(dstSize.width * dstSize.height)
        for (ry in 0 until rawH) {
            for (rx in 0 until rawW) {
                val (ux, uy) = forwardMap(rx, ry, rawW, rawH, orientation)
                out[uy * dstSize.width + ux] = src.pixels[ry * rawW + rx]
            }
        }
        return PixelBuffer(dstSize.width, dstSize.height, out)
    }

    private fun forwardMap(
        rx: Int,
        ry: Int,
        rawW: Int,
        rawH: Int,
        orientation: Int,
    ): Pair<Int, Int> =
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> (rawW - 1 - rx) to ry
            ExifInterface.ORIENTATION_ROTATE_180 -> (rawW - 1 - rx) to (rawH - 1 - ry)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> rx to (rawH - 1 - ry)
            ExifInterface.ORIENTATION_TRANSPOSE -> ry to rx
            ExifInterface.ORIENTATION_ROTATE_90 -> (rawH - 1 - ry) to rx
            ExifInterface.ORIENTATION_TRANSVERSE -> (rawH - 1 - ry) to (rawW - 1 - rx)
            ExifInterface.ORIENTATION_ROTATE_270 -> ry to (rawW - 1 - rx)
            else -> rx to ry
        }
}
