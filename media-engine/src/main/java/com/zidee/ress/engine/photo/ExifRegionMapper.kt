package com.zidee.ress.engine.photo

import androidx.exifinterface.media.ExifInterface
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize

/**
 * Pure-Kotlin coordinate mapper and memory-bounded sample-size calculator for EXIF-aware
 * region decoding (`BitmapRegionDecoder`).
 */
object ExifRegionMapper {
    /**
     * Hard pixel ceiling (~16 MP = ~64 MiB ARGB_8888) so decoding huge photos (e.g. 108 MP / 12000x9000)
     * never causes an OutOfMemoryError on mid/low-RAM Android devices.
     */
    const val DEFAULT_MAX_DECODED_PIXELS: Long = 16_000_000L

    fun orientedSize(
        rawSize: PixelSize,
        exifOrientation: Int,
    ): PixelSize =
        when (exifOrientation) {
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
            -> PixelSize(width = rawSize.height, height = rawSize.width)
            else -> rawSize
        }

    fun scaleRegion(
        region: PixelRect,
        fromSize: PixelSize,
        toSize: PixelSize,
    ): PixelRect {
        if (fromSize == toSize) return clampRect(region, toSize)
        val scaleX = toSize.width.toDouble() / fromSize.width
        val scaleY = toSize.height.toDouble() / fromSize.height
        val left = (region.left * scaleX).toInt().coerceIn(0, toSize.width - 1)
        val top = (region.top * scaleY).toInt().coerceIn(0, toSize.height - 1)
        val width = (region.width * scaleX).toInt().coerceIn(1, toSize.width - left)
        val height = (region.height * scaleY).toInt().coerceIn(1, toSize.height - top)
        return PixelRect(left = left, top = top, width = width, height = height)
    }

    /**
     * Maps a crop rectangle expressed in EXIF-oriented (upright) pixel coordinates back into
     * the raw encoded sensor coordinate space for `BitmapRegionDecoder.decodeRegion`.
     */
    fun mapOrientedCropToRaw(
        orientedCrop: PixelRect,
        rawSize: PixelSize,
        exifOrientation: Int,
    ): PixelRect {
        val upright = orientedSize(rawSize, exifOrientation)
        val clamped = clampRect(orientedCrop, upright)
        val uL = clamped.left
        val uT = clamped.top
        val uR = clamped.left + clamped.width
        val uB = clamped.top + clamped.height
        val rawW = rawSize.width
        val rawH = rawSize.height

        val mapped =
            when (exifOrientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                    PixelRect(left = rawW - uR, top = uT, width = clamped.width, height = clamped.height)
                ExifInterface.ORIENTATION_ROTATE_180 ->
                    PixelRect(left = rawW - uR, top = rawH - uB, width = clamped.width, height = clamped.height)
                ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                    PixelRect(left = uL, top = rawH - uB, width = clamped.width, height = clamped.height)
                ExifInterface.ORIENTATION_TRANSPOSE ->
                    PixelRect(left = uT, top = uL, width = clamped.height, height = clamped.width)
                ExifInterface.ORIENTATION_ROTATE_90 ->
                    PixelRect(left = uT, top = rawH - uR, width = clamped.height, height = clamped.width)
                ExifInterface.ORIENTATION_TRANSVERSE ->
                    PixelRect(left = rawW - uB, top = rawH - uR, width = clamped.height, height = clamped.width)
                ExifInterface.ORIENTATION_ROTATE_270 ->
                    PixelRect(left = rawW - uB, top = uL, width = clamped.height, height = clamped.width)
                else -> clamped
            }
        return clampRect(mapped, rawSize)
    }

    /**
     * Computes a power-of-two `inSampleSize` that preserves at least 2x the target resolution
     * for crisp Lanczos-3 downsampling while strictly enforcing [maxDecodedPixels].
     */
    fun computeBoundedSampleSize(
        decodeWidth: Int,
        decodeHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        maxDecodedPixels: Long = DEFAULT_MAX_DECODED_PIXELS,
    ): Int {
        var sample = 1
        while (decodeWidth / (sample * 2) >= targetWidth * 2 &&
            decodeHeight / (sample * 2) >= targetHeight * 2
        ) {
            sample *= 2
        }
        while (decodedPixelCount(decodeWidth, decodeHeight, sample) > maxDecodedPixels &&
            decodeWidth / (sample * 2) >= 1 &&
            decodeHeight / (sample * 2) >= 1
        ) {
            sample *= 2
        }
        return sample
    }

    private fun decodedPixelCount(
        width: Int,
        height: Int,
        sample: Int,
    ): Long = (width / sample).coerceAtLeast(1).toLong() * (height / sample).coerceAtLeast(1).toLong()

    private fun clampRect(
        rect: PixelRect,
        bounds: PixelSize,
    ): PixelRect {
        val left = rect.left.coerceIn(0, bounds.width - 1)
        val top = rect.top.coerceIn(0, bounds.height - 1)
        val width = rect.width.coerceIn(1, bounds.width - left)
        val height = rect.height.coerceIn(1, bounds.height - top)
        return PixelRect(left = left, top = top, width = width, height = height)
    }
}
