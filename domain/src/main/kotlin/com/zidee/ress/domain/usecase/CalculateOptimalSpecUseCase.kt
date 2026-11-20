package com.zidee.ress.domain.usecase

import com.zidee.ress.domain.model.BitrateMode
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.CropAdjustment
import com.zidee.ress.domain.model.FitRule
import com.zidee.ress.domain.model.FrameRateMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.PhotoEncodingSpec
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.PlatformPreset
import com.zidee.ress.domain.model.VideoAdvancedConfig
import com.zidee.ress.domain.model.VideoEncodingSpec
import com.zidee.ress.domain.model.VideoResolution
import com.zidee.ress.domain.model.preset
import com.zidee.ress.domain.usecase.EvenDimensions.floorEven
import com.zidee.ress.domain.usecase.EvenDimensions.roundEven
import com.zidee.ress.domain.usecase.EvenDimensions.roundEvenOffset
import kotlin.math.min

/**
 * Turns a source file + platform + canvas mode + Advanced Pro settings into the exact
 * output geometry and encoder parameters. Pure and synchronous: no I/O, safe to call on
 * every slider tick.
 *
 * Geometry rules:
 * - Canvas modes ([CanvasAspectMode.BLUR_BG_9_16], [CanvasAspectMode.CROP_9_16]) are always
 *   exact 1080x1920, including when [VideoResolution.HD_720P_ULTRA_SHARP] is selected.
 * - [CanvasAspectMode.ORIGINAL] follows the platform [FitRule] (scaled by 2/3 when
 *   [VideoResolution.HD_720P_ULTRA_SHARP] is chosen for video). Only IG Post's width lock may
 *   upscale; every other rule only downsizes.
 */
class CalculateOptimalSpecUseCase {
    operator fun invoke(request: OptimizationRequest): OptimalMediaSpec {
        val source = request.source
        val preset = request.platform.preset
        val sourceSize = source.displaySize
        val scale = budgetScale(source.mediaType, request.videoConfig)

        val canvas = PixelSize(CANVAS_WIDTH, CANVAS_HEIGHT)
        val (outputSize, geometry) =
            when (request.aspectMode) {
                CanvasAspectMode.ORIGINAL -> fitOriginal(sourceSize, originalRule(request, preset).scaled(scale)) to CanvasGeometry.Scale
                CanvasAspectMode.BLUR_BG_9_16 -> canvas to blurBackground(sourceSize, canvas, request.blurRadiusPx)
                CanvasAspectMode.CROP_9_16 -> canvas to crop(sourceSize, request.crop)
            }

        val photo = if (source.mediaType == MediaType.PHOTO) photoSpec(request, preset) else null
        val video = if (source.mediaType == MediaType.VIDEO) videoSpec(source, request.videoConfig, preset) else null

        return OptimalMediaSpec(
            platform = request.platform,
            aspectMode = request.aspectMode,
            mediaType = source.mediaType,
            sourceSize = sourceSize,
            outputSize = outputSize,
            geometry = geometry,
            photo = photo,
            video = video,
            estimatedSizeBytes = estimateBytes(outputSize, photo, video, source.durationMs),
        )
    }

    private fun budgetScale(
        type: MediaType,
        config: VideoAdvancedConfig,
    ): Double =
        if (type == MediaType.VIDEO && config.resolution == VideoResolution.HD_720P_ULTRA_SHARP) {
            HD_720P_SCALE
        } else {
            1.0
        }

    private fun originalRule(
        request: OptimizationRequest,
        preset: PlatformPreset,
    ): FitRule =
        when (request.source.mediaType) {
            MediaType.VIDEO -> preset.videoOriginalFit
            MediaType.PHOTO -> {
                val override = request.photoConfig.targetResolution
                if (override != null && preset.acceptsPhotoTargetResolution) {
                    FitRule.OrientedBox(longEdge = override.longEdgePx, shortEdge = override.longEdgePx)
                } else {
                    preset.photoOriginalFit
                }
            }
        }

    private fun FitRule.scaled(factor: Double): FitRule {
        if (factor == 1.0) return this
        return when (this) {
            is FitRule.OrientedBox -> FitRule.OrientedBox(roundEven(longEdge * factor), roundEven(shortEdge * factor))
            is FitRule.PortraitBox -> FitRule.PortraitBox(roundEven(width * factor), roundEven(height * factor))
            is FitRule.LockWidth -> FitRule.LockWidth(roundEven(width * factor))
        }
    }

    private fun fitOriginal(
        source: PixelSize,
        rule: FitRule,
    ): PixelSize =
        when (rule) {
            is FitRule.OrientedBox ->
                if (source.isPortrait) {
                    fitInBox(source, rule.shortEdge, rule.longEdge, allowUpscale = false)
                } else {
                    fitInBox(source, rule.longEdge, rule.shortEdge, allowUpscale = false)
                }
            is FitRule.PortraitBox -> fitInBox(source, rule.width, rule.height, allowUpscale = false)
            is FitRule.LockWidth ->
                PixelSize(
                    width = rule.width,
                    height = roundEven(source.height * rule.width.toDouble() / source.width),
                )
        }

    private fun fitInBox(
        source: PixelSize,
        boxWidth: Int,
        boxHeight: Int,
        allowUpscale: Boolean,
    ): PixelSize {
        val fit = min(boxWidth.toDouble() / source.width, boxHeight.toDouble() / source.height)
        val factor = if (allowUpscale) fit else min(fit, 1.0)
        val width = roundEven(source.width * factor)
        val height = roundEven(source.height * factor)
        return if (allowUpscale) {
            PixelSize(width, height)
        } else {
            PixelSize(min(width, floorEven(source.width)), min(height, floorEven(source.height)))
        }
    }

    private fun blurBackground(
        source: PixelSize,
        canvas: PixelSize,
        blurRadiusPx: Int,
    ): CanvasGeometry.BlurBackground {
        val fg = fitInBox(source, canvas.width, canvas.height, allowUpscale = true)
        return CanvasGeometry.BlurBackground(
            foreground =
                PixelRect(
                    left = (canvas.width - fg.width) / 2,
                    top = (canvas.height - fg.height) / 2,
                    width = fg.width,
                    height = fg.height,
                ),
            blurRadiusPx = blurRadiusPx,
        )
    }

    private fun crop(
        source: PixelSize,
        adjustment: CropAdjustment,
    ): CanvasGeometry.Crop {
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
        return CanvasGeometry.Crop(
            PixelRect(
                left = roundEvenOffset(maxLeft * adjustment.panX).coerceIn(0, maxLeft),
                top = roundEvenOffset(maxTop * adjustment.panY).coerceIn(0, maxTop),
                width = width,
                height = height,
            ),
        )
    }

    private fun photoSpec(
        request: OptimizationRequest,
        preset: PlatformPreset,
    ): PhotoEncodingSpec {
        val config = request.photoConfig
        return PhotoEncodingSpec(
            scalingAlgorithm = config.scalingAlgorithm,
            sharpnessPercent = config.sharpnessPercent,
            unsharpAmount = config.sharpnessPercent / 100.0 * MAX_UNSHARP_ALPHA,
            jpegQuality = config.jpegQuality,
            convertToSrgb = config.autoSrgbFix || preset.forceSdr,
        )
    }

    private fun videoSpec(
        source: MediaMetadata,
        config: VideoAdvancedConfig,
        preset: PlatformPreset,
    ): VideoEncodingSpec {
        val maxKbps =
            when (val mode = config.bitrateMode) {
                BitrateMode.AutoSweetSpot -> preset.videoMaxBitrateKbps
                is BitrateMode.Manual -> mode.kbps
            }
        val (segmentSeconds, segments) = planSegments(source.durationMs, config.autoSplit.segmentSeconds)
        return VideoEncodingSpec(
            frameRate = frameRate(config.frameRateMode, source.frameRate),
            encoder = config.encoder,
            maxBitrateKbps = maxKbps,
            bufferSizeKbps = maxKbps * BUFFER_MULTIPLIER,
            audioBitrateKbps = AUDIO_BITRATE_KBPS,
            audioSampleRateHz = AUDIO_SAMPLE_RATE_HZ,
            sharpen = config.antiBlurSharpening,
            applyHdrToSdr = source.isHdr && (config.hdrToSdr || preset.forceSdr),
            segmentSeconds = segmentSeconds,
            segmentDurationsMs = segments,
        )
    }

    private fun frameRate(
        mode: FrameRateMode,
        sourceFps: Double?,
    ): Double {
        val requested =
            when (mode) {
                FrameRateMode.LOCK_30 -> FPS_30
                FrameRateMode.FPS_60 -> FPS_60
                FrameRateMode.ORIGINAL -> sourceFps ?: FPS_30
            }
        return if (sourceFps != null) min(requested, sourceFps) else requested
    }

    private fun planSegments(
        durationMs: Long?,
        segmentSeconds: Int?,
    ): Pair<Int?, List<Long>> {
        if (durationMs == null) return segmentSeconds to emptyList()
        val segmentMs = segmentSeconds?.let { it * MS_PER_SECOND }
        if (segmentMs == null || durationMs <= segmentMs) return null to listOf(durationMs)

        val full = (durationMs / segmentMs).coerceAtMost(MAX_PLANNED_SEGMENTS.toLong()).toInt()
        val tail = durationMs - full * segmentMs
        val parts = List(full) { segmentMs } + if (tail > 0L) listOf(tail) else emptyList()
        return segmentSeconds to parts
    }

    private fun estimateBytes(
        output: PixelSize,
        photo: PhotoEncodingSpec?,
        video: VideoEncodingSpec?,
        durationMs: Long?,
    ): Long? =
        when {
            video != null -> durationMs?.let { (video.maxBitrateKbps + video.audioBitrateKbps).toLong() * it / BITS_PER_BYTE }
            photo != null -> {
                val bitsPerPixel = JPEG_BASE_BPP + (photo.jpegQuality - JPEG_BASE_QUALITY) * JPEG_BPP_PER_QUALITY
                (output.pixelCount * bitsPerPixel / BITS_PER_BYTE).toLong()
            }
            else -> null
        }

    private companion object {
        const val CANVAS_WIDTH = 1080
        const val CANVAS_HEIGHT = 1920
        const val TARGET_ASPECT = 9.0 / 16.0
        const val HD_720P_SCALE = 720.0 / 1080.0

        const val MAX_UNSHARP_ALPHA = 1.5

        const val FPS_30 = 30.0
        const val FPS_60 = 60.0
        const val BUFFER_MULTIPLIER = 2
        const val AUDIO_BITRATE_KBPS = 128
        const val AUDIO_SAMPLE_RATE_HZ = 44_100
        const val MS_PER_SECOND = 1_000L
        const val MAX_PLANNED_SEGMENTS = 1_000
        const val BITS_PER_BYTE = 8

        // Rough JPEG heuristic for UI estimates only: ~1.5 bpp at q80, +0.15 bpp per quality step.
        const val JPEG_BASE_QUALITY = 80
        const val JPEG_BASE_BPP = 1.5
        const val JPEG_BPP_PER_QUALITY = 0.15
    }
}
