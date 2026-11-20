package com.zidee.ress.engine.photo

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.engine.MediaInputUriGuard
import java.io.IOException

/**
 * Decodes a photo URI with EXIF orientation applied and converts wide-gamut colour spaces
 * (Display P3, AdobeRGB, etc.) to 8-bit sRGB. When cropping a large photo, uses EXIF-aware
 * [BitmapRegionDecoder] with a hard decoded-pixel ceiling to prevent OutOfMemoryError.
 */
class ColorSpaceNormalizer(private val context: Context) {
    data class DecodedPhoto(
        val buffer: PixelBuffer,
        val sampleSizeUsed: Int,
        val colorSpace: ColorSpace?,
        val alreadyCropped: Boolean = false,
    )

    fun decodeAndNormalize(
        uri: Uri,
        targetSize: PixelSize,
        convertToSrgb: Boolean = true,
        orientedCropRegion: PixelRect? = null,
        cropReferenceSize: PixelSize? = null,
        maxDecodedPixels: Long = ExifRegionMapper.DEFAULT_MAX_DECODED_PIXELS,
    ): DecodedPhoto {
        ensureSupportedScheme(uri)
        val resolver = context.contentResolver
        val exifOrientation = readExifOrientation(resolver, uri)
        val rawSize = readRawImageSize(resolver, uri)
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB)

        if (orientedCropRegion != null && rawSize != null) {
            val regionAttempt =
                tryDecodeRegion(
                    resolver = resolver,
                    uri = uri,
                    rawSize = rawSize,
                    exifOrientation = exifOrientation,
                    orientedCropRegion = orientedCropRegion,
                    cropReferenceSize = cropReferenceSize,
                    targetSize = targetSize,
                    convertToSrgb = convertToSrgb,
                    srgb = srgb,
                    maxDecodedPixels = maxDecodedPixels,
                )
            if (regionAttempt != null) {
                return regionAttempt
            }
        }

        val fallbackSample =
            if (rawSize != null) {
                val oriented = ExifRegionMapper.orientedSize(rawSize, exifOrientation)
                ExifRegionMapper.computeBoundedSampleSize(
                    decodeWidth = oriented.width,
                    decodeHeight = oriented.height,
                    targetWidth = targetSize.width,
                    targetHeight = targetSize.height,
                    maxDecodedPixels = maxDecodedPixels,
                )
            } else {
                1
            }

        val options = buildDecodeOptions(fallbackSample, convertToSrgb, srgb)
        val rawBitmap =
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: throw IOException("Unable to decode image from URI")

        return finalizeBitmap(
            rawBitmap = rawBitmap,
            exifOrientation = exifOrientation,
            convertToSrgb = convertToSrgb,
            srgb = srgb,
            sampleSizeUsed = options.inSampleSize,
            alreadyCropped = false,
        )
    }

    @Suppress("LongParameterList")
    private fun tryDecodeRegion(
        resolver: ContentResolver,
        uri: Uri,
        rawSize: PixelSize,
        exifOrientation: Int,
        orientedCropRegion: PixelRect,
        cropReferenceSize: PixelSize?,
        targetSize: PixelSize,
        convertToSrgb: Boolean,
        srgb: ColorSpace,
        maxDecodedPixels: Long,
    ): DecodedPhoto? {
        val actualOriented = ExifRegionMapper.orientedSize(rawSize, exifOrientation)
        val scaledOrientedCrop =
            if (cropReferenceSize != null) {
                ExifRegionMapper.scaleRegion(orientedCropRegion, cropReferenceSize, actualOriented)
            } else {
                ExifRegionMapper.scaleRegion(orientedCropRegion, actualOriented, actualOriented)
            }
        val rawCrop = ExifRegionMapper.mapOrientedCropToRaw(scaledOrientedCrop, rawSize, exifOrientation)
        val regionSample =
            ExifRegionMapper.computeBoundedSampleSize(
                decodeWidth = scaledOrientedCrop.width,
                decodeHeight = scaledOrientedCrop.height,
                targetWidth = targetSize.width,
                targetHeight = targetSize.height,
                maxDecodedPixels = maxDecodedPixels,
            )
        val options = buildDecodeOptions(regionSample, convertToSrgb, srgb)
        val androidRect = Rect(rawCrop.left, rawCrop.top, rawCrop.left + rawCrop.width, rawCrop.top + rawCrop.height)

        val regionBitmap = decodeRegionSafely(resolver, uri, androidRect, options) ?: return null
        return finalizeBitmap(
            rawBitmap = regionBitmap,
            exifOrientation = exifOrientation,
            convertToSrgb = convertToSrgb,
            srgb = srgb,
            sampleSizeUsed = options.inSampleSize,
            alreadyCropped = true,
        )
    }

    private fun decodeRegionSafely(
        resolver: ContentResolver,
        uri: Uri,
        rect: Rect,
        options: BitmapFactory.Options,
    ): Bitmap? =
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val decoder = createRegionDecoder(stream) ?: return null
                try {
                    decoder.decodeRegion(rect, options)
                } finally {
                    decoder.recycle()
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun createRegionDecoder(stream: java.io.InputStream): BitmapRegionDecoder? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(stream)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(stream, false)
        }

    private fun finalizeBitmap(
        rawBitmap: Bitmap,
        exifOrientation: Int,
        convertToSrgb: Boolean,
        srgb: ColorSpace,
        sampleSizeUsed: Int,
        alreadyCropped: Boolean,
    ): DecodedPhoto {
        var orientedBitmap: Bitmap? = null
        var srgbBitmap: Bitmap? = null
        try {
            orientedBitmap = applyExifOrientation(rawBitmap, exifOrientation)
            val targetBitmap =
                if (convertToSrgb && orientedBitmap.colorSpace != srgb) {
                    convertBitmapToSrgb(orientedBitmap, srgb).also { srgbBitmap = it }
                } else {
                    orientedBitmap
                }
            val pixels = IntArray(targetBitmap.width * targetBitmap.height)
            targetBitmap.getPixels(pixels, 0, targetBitmap.width, 0, 0, targetBitmap.width, targetBitmap.height)
            return DecodedPhoto(
                buffer = PixelBuffer(targetBitmap.width, targetBitmap.height, pixels),
                sampleSizeUsed = sampleSizeUsed,
                colorSpace = targetBitmap.colorSpace,
                alreadyCropped = alreadyCropped,
            )
        } finally {
            if (srgbBitmap != null && srgbBitmap !== orientedBitmap) {
                srgbBitmap?.recycle()
            }
            if (orientedBitmap != null && orientedBitmap !== rawBitmap) {
                orientedBitmap.recycle()
            }
            if (!rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
        }
    }

    private fun buildDecodeOptions(
        sampleSize: Int,
        convertToSrgb: Boolean,
        srgb: ColorSpace,
    ): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize.coerceAtLeast(1)
            if (convertToSrgb) {
                inPreferredColorSpace = srgb
            }
        }

    private fun readRawImageSize(
        resolver: ContentResolver,
        uri: Uri,
    ): PixelSize? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        return if (width > 0 && height > 0) PixelSize(width, height) else null
    }

    private fun readExifOrientation(
        resolver: ContentResolver,
        uri: Uri,
    ): Int =
        try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun applyExifOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun convertBitmapToSrgb(
        source: Bitmap,
        srgb: ColorSpace,
    ): Bitmap {
        val target =
            Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888,
                false,
                srgb,
            )
        val canvas = Canvas(target)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return target
    }

    internal fun ensureSupportedScheme(uri: Uri) {
        MediaInputUriGuard.ensureSafeInputUri(context, uri)
    }
}
