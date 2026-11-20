package com.zidee.ress.presentation.studio

import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.CropAdjustment
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pixel-exact source and destination rectangles for drawing a `CROP_9_16` preview
 * 1:1 with the domain export geometry ([CanvasGeometry.Crop]).
 */
internal data class CropDrawRects(
    val srcLeft: Int,
    val srcTop: Int,
    val srcWidth: Int,
    val srcHeight: Int,
    val dstLeft: Int,
    val dstTop: Int,
    val dstWidth: Int,
    val dstHeight: Int,
)

/**
 * Pure pixel-space geometry bridge ensuring the Compose preview matches
 * [com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase] across zoom 1x, >1x,
 * and every screen density without dp/px conversion drift.
 */
internal object CropPreviewGeometry {
    private const val TARGET_ASPECT = 9.0 / 16.0
    private const val DEFAULT_OUTPUT_WIDTH = 1080
    private const val DEFAULT_OUTPUT_HEIGHT = 1920

    fun resolveCropDrawRects(
        bitmapSize: PixelSize,
        containerWidthPx: Int,
        containerHeightPx: Int,
        activeSpec: OptimalMediaSpec?,
        cropAdjustment: CropAdjustment,
    ): CropDrawRects {
        val (sourceSize, sourceRegion, outputSize) =
            resolveExportGeometry(
                bitmapSize = bitmapSize,
                activeSpec = activeSpec,
                cropAdjustment = cropAdjustment,
            )
        val scaledSrc = scaleRegionToBitmap(sourceRegion, sourceSize, bitmapSize)
        val dst = fitAspectInContainer(containerWidthPx, containerHeightPx, outputSize)
        return CropDrawRects(
            srcLeft = scaledSrc.left,
            srcTop = scaledSrc.top,
            srcWidth = scaledSrc.width,
            srcHeight = scaledSrc.height,
            dstLeft = dst.left,
            dstTop = dst.top,
            dstWidth = dst.width,
            dstHeight = dst.height,
        )
    }

    fun fitAspectInContainer(
        containerWidthPx: Int,
        containerHeightPx: Int,
        outputSize: PixelSize = PixelSize(DEFAULT_OUTPUT_WIDTH, DEFAULT_OUTPUT_HEIGHT),
    ): PixelRect {
        val safeW = containerWidthPx.coerceAtLeast(1)
        val safeH = containerHeightPx.coerceAtLeast(1)
        val targetAspect = outputSize.width.toDouble() / outputSize.height.toDouble()
        val containerAspect = safeW.toDouble() / safeH.toDouble()
        val dstWidth: Int
        val dstHeight: Int
        if (containerAspect > targetAspect) {
            dstHeight = safeH
            dstWidth = (safeH * targetAspect).roundToInt().coerceIn(1, safeW)
        } else {
            dstWidth = safeW
            dstHeight = (safeW / targetAspect).roundToInt().coerceIn(1, safeH)
        }
        val dstLeft = (safeW - dstWidth) / 2
        val dstTop = (safeH - dstHeight) / 2
        return PixelRect(left = dstLeft, top = dstTop, width = dstWidth, height = dstHeight)
    }

    /**
     * Converts a pointer drag in physical pixels into a normalized `[0.0, 1.0]` pan delta
     * so dragging 1 viewport pixel shifts the source crop window by 1 visible pixel.
     */
    fun computePanDelta(
        dragDeltaPx: Float,
        viewportSpanPx: Float,
        sourceSpanPx: Int,
        cropSpanPx: Int,
    ): Double {
        val maxOffset = sourceSpanPx - cropSpanPx
        if (viewportSpanPx <= 0f || maxOffset <= 0 || cropSpanPx <= 0) return 0.0
        return -(dragDeltaPx.toDouble() / viewportSpanPx.toDouble()) * (cropSpanPx.toDouble() / maxOffset.toDouble())
    }

    private fun resolveExportGeometry(
        bitmapSize: PixelSize,
        activeSpec: OptimalMediaSpec?,
        cropAdjustment: CropAdjustment,
    ): Triple<PixelSize, PixelRect, PixelSize> {
        val specCrop = activeSpec?.geometry as? CanvasGeometry.Crop
        if (activeSpec != null && specCrop != null) {
            return Triple(activeSpec.sourceSize, specCrop.sourceRegion, activeSpec.outputSize)
        }
        val fallbackRegion = computeFallbackCropRegion(bitmapSize, cropAdjustment)
        return Triple(bitmapSize, fallbackRegion, PixelSize(DEFAULT_OUTPUT_WIDTH, DEFAULT_OUTPUT_HEIGHT))
    }

    private fun scaleRegionToBitmap(
        region: PixelRect,
        fromSize: PixelSize,
        toSize: PixelSize,
    ): PixelRect {
        if (fromSize == toSize) return clampRect(region, toSize)
        val scaleX = toSize.width.toDouble() / fromSize.width.toDouble()
        val scaleY = toSize.height.toDouble() / fromSize.height.toDouble()
        val left = (region.left * scaleX).roundToInt().coerceIn(0, (toSize.width - 1).coerceAtLeast(0))
        val top = (region.top * scaleY).roundToInt().coerceIn(0, (toSize.height - 1).coerceAtLeast(0))
        val width = (region.width * scaleX).roundToInt().coerceIn(1, (toSize.width - left).coerceAtLeast(1))
        val height = (region.height * scaleY).roundToInt().coerceIn(1, (toSize.height - top).coerceAtLeast(1))
        return PixelRect(left = left, top = top, width = width, height = height)
    }

    private fun computeFallbackCropRegion(
        source: PixelSize,
        adjustment: CropAdjustment,
    ): PixelRect {
        val (baseWidth, baseHeight) =
            if (source.aspectRatio > TARGET_ASPECT) {
                source.height * TARGET_ASPECT to source.height.toDouble()
            } else {
                source.width.toDouble() to source.width / TARGET_ASPECT
            }
        val width = min(roundEven(baseWidth / adjustment.zoom), floorEven(source.width))
        val height = min(roundEven(baseHeight / adjustment.zoom), floorEven(source.height))
        val maxLeft = (source.width - width).coerceAtLeast(0)
        val maxTop = (source.height - height).coerceAtLeast(0)
        val left = roundEvenOffset(maxLeft * adjustment.panX).coerceIn(0, maxLeft)
        val top = roundEvenOffset(maxTop * adjustment.panY).coerceIn(0, maxTop)
        return PixelRect(left = left, top = top, width = width, height = height)
    }

    private fun clampRect(
        rect: PixelRect,
        bounds: PixelSize,
    ): PixelRect {
        val left = rect.left.coerceIn(0, (bounds.width - 1).coerceAtLeast(0))
        val top = rect.top.coerceIn(0, (bounds.height - 1).coerceAtLeast(0))
        val width = rect.width.coerceIn(1, (bounds.width - left).coerceAtLeast(1))
        val height = rect.height.coerceIn(1, (bounds.height - top).coerceAtLeast(1))
        return PixelRect(left = left, top = top, width = width, height = height)
    }

    private fun roundEven(value: Double): Int = ((value / 2.0).roundToInt() * 2).coerceAtLeast(2)

    private fun floorEven(value: Int): Int = (value and 1.inv()).coerceAtLeast(2)

    private fun roundEvenOffset(value: Double): Int = ((value / 2.0).roundToInt() * 2).coerceAtLeast(0)
}
