@file:Suppress("MatchingDeclarationName")

package com.zidee.ress.presentation.common

import com.zidee.ress.core.format.MediaFormatters
import com.zidee.ress.designsystem.component.AdvancedTuningEvent
import com.zidee.ress.designsystem.component.AdvancedTuningUiState
import com.zidee.ress.designsystem.component.AutoSplitOption
import com.zidee.ress.designsystem.component.CanvasModeIconKind
import com.zidee.ress.designsystem.component.CanvasModeOption
import com.zidee.ress.designsystem.component.EncoderOption
import com.zidee.ress.designsystem.component.FrameRateOption
import com.zidee.ress.designsystem.component.MediaTuningMode
import com.zidee.ress.designsystem.component.PlatformPresetOption
import com.zidee.ress.designsystem.component.ResamplingOption
import com.zidee.ress.designsystem.component.VideoResolutionOption
import com.zidee.ress.domain.model.AutoSplitMode
import com.zidee.ress.domain.model.BitrateMode
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.FrameRateMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.PhotoAdvancedConfig
import com.zidee.ress.domain.model.PhotoTargetResolution
import com.zidee.ress.domain.model.ScalingAlgorithm
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoAdvancedConfig
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.model.VideoResolution
import com.zidee.ress.domain.model.ZideeAlbum
import com.zidee.ress.domain.model.preset
import java.util.Locale
import kotlin.math.roundToInt

object ZideePresentationDefaults {
    val platformOptions: List<PlatformPresetOption> =
        TargetPlatform.entries.map { platform ->
            PlatformPresetOption(
                id = platform.name,
                label = platform.toShortLabel(),
                contentDescription = platform.toFullTitle(),
            )
        }

    val canvasModeOptions: List<CanvasModeOption> =
        listOf(
            CanvasModeOption(
                id = CanvasAspectMode.ORIGINAL.name,
                label = "Rasio Asli",
                iconKind = CanvasModeIconKind.ORIGINAL,
                contentDescription = "Rasio Asli",
            ),
            CanvasModeOption(
                id = CanvasAspectMode.BLUR_BG_9_16.name,
                label = "Blur BG 9:16",
                iconKind = CanvasModeIconKind.BLUR_BG_9_16,
                contentDescription = "Latar Blur 9 banding 16",
            ),
            CanvasModeOption(
                id = CanvasAspectMode.CROP_9_16.name,
                label = "Crop 9:16",
                iconKind = CanvasModeIconKind.CROP_9_16,
                contentDescription = "Potong 9 banding 16",
            ),
        )
}

fun TargetPlatform.toShortLabel(): String =
    when (this) {
        TargetPlatform.WA_STORY -> "WA Story"
        TargetPlatform.WA_CHAT -> "WA Chat"
        TargetPlatform.IG_STORY -> "IG Story"
        TargetPlatform.IG_POST -> "IG Post"
        TargetPlatform.IG_CHAT -> "DM"
    }

fun TargetPlatform.toFullTitle(): String =
    when (this) {
        TargetPlatform.WA_STORY -> "WA Story"
        TargetPlatform.WA_CHAT -> "WA Chat"
        TargetPlatform.IG_STORY -> "IG Story"
        TargetPlatform.IG_POST -> "IG Post"
        TargetPlatform.IG_CHAT -> "IG DM"
    }

fun TargetPlatform.toCompactBadge(): String =
    when (this) {
        TargetPlatform.WA_STORY -> "WA STORY · 9:16"
        TargetPlatform.WA_CHAT -> "WA CHAT"
        TargetPlatform.IG_STORY -> "IG STORY · 9:16"
        TargetPlatform.IG_POST -> "IG POST"
        TargetPlatform.IG_CHAT -> "IG DM"
    }

fun TargetPlatform.toActionTargetShort(): String =
    when (this) {
        TargetPlatform.WA_STORY, TargetPlatform.WA_CHAT -> "WA"
        TargetPlatform.IG_STORY, TargetPlatform.IG_POST, TargetPlatform.IG_CHAT -> "IG"
    }

fun TargetPlatform.toDirectPackageHint(): String =
    when (this) {
        TargetPlatform.WA_STORY, TargetPlatform.WA_CHAT ->
            "Direct Intent: com.whatsapp · Tanpa buka chooser ulang"
        TargetPlatform.IG_STORY, TargetPlatform.IG_POST, TargetPlatform.IG_CHAT ->
            "Direct Intent: com.instagram.android · Tanpa buka chooser ulang"
    }

fun CanvasAspectMode.toShortLabel(): String =
    when (this) {
        CanvasAspectMode.ORIGINAL -> "Rasio Asli"
        CanvasAspectMode.BLUR_BG_9_16 -> "Blur BG 9:16"
        CanvasAspectMode.CROP_9_16 -> "Crop 9:16"
    }

fun MediaUri.extractDisplayName(): String {
    val raw = value.substringAfterLast('/').substringBefore('?')
    return if (raw.isBlank()) "media_zidee" else raw
}

fun MediaMetadata?.toSourceSpecComparisonLine(spec: OptimalMediaSpec?): String {
    if (this == null) {
        return "Memeriksa metadata media lokal..."
    }
    val sourceSize = displaySize
    val sourceRes = MediaFormatters.formatResolution(sourceSize.width, sourceSize.height)
    val fpsPart = frameRate?.let { " ${it.roundToInt()}fps" }.orEmpty()
    val sizePart = MediaFormatters.formatBytes(sizeBytes)
    val left = "ASLI: $sourceRes$fpsPart $sizePart"
    if (spec == null) return left

    val targetRes = MediaFormatters.formatResolution(spec.outputSize.width, spec.outputSize.height)
    val targetFps = spec.video?.frameRate?.let { " ${it.roundToInt()}fps" }.orEmpty()
    val targetSize = spec.estimatedSizeBytes?.let { " ~${MediaFormatters.formatBytes(it)}" }.orEmpty()
    return "$left → TARGET: $targetRes$targetFps$targetSize"
}

fun MediaMetadata?.toAsliSummaryLine(): String {
    if (this == null) return "Asli   : Memeriksa..."
    val sourceSize = displaySize
    val res = MediaFormatters.formatResolution(sourceSize.width, sourceSize.height)
    val fps = frameRate?.let { " ${it.roundToInt()}fps" }.orEmpty()
    val hdr = if (isHdr) " · HDR" else ""
    val bytes = MediaFormatters.formatBytes(sizeBytes)
    return "Asli   : $res$fps$hdr · $bytes"
}

fun OptimalMediaSpec?.toTargetSummaryLine(): String {
    if (this == null) return "Target : Menghitung sweet-spot..."
    val res = MediaFormatters.formatResolution(outputSize.width, outputSize.height)
    val fps = video?.frameRate?.let { " · ${it.roundToInt()}fps" }.orEmpty()
    val est = estimatedSizeBytes?.let { " · ~${MediaFormatters.formatBytes(it)}" }.orEmpty()
    return "Target : $res$fps$est"
}

fun OptimalMediaSpec?.toCanvasStatusPill(
    aspectMode: CanvasAspectMode,
    blurRadiusPx: Int =
        (this?.geometry as? CanvasGeometry.BlurBackground)?.blurRadiusPx
            ?: CanvasGeometry.DEFAULT_BLUR_RADIUS_PX,
): String {
    val resText =
        this?.let { MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height) }
            ?: "1080 × 1920"
    return when (aspectMode) {
        CanvasAspectMode.ORIGINAL -> "Rasio Asli · $resText"
        CanvasAspectMode.BLUR_BG_9_16 -> "Kanvas 9:16 · Blur BG Radius: ${blurRadiusPx}px"
        CanvasAspectMode.CROP_9_16 -> "Crop 9:16 · Geser & Zoom Fokus"
    }
}

fun MediaType.toDestinationAlbumHint(): String = "Wajib Simpan: ${ZideeAlbum.relativePathFor(this)}"

fun formatAdvancedSummaryBadge(
    mediaType: MediaType?,
    photoConfig: PhotoAdvancedConfig,
    videoConfig: VideoAdvancedConfig,
): String {
    val algo = if (photoConfig.scalingAlgorithm == ScalingAlgorithm.LANCZOS3) "Lanczos3" else "Bicubic"
    val usm = "USM ${photoConfig.sharpnessPercent}%"
    val encoder = if (videoConfig.encoder == VideoEncoder.CPU_LIBX264) "x264" else "GPU"
    return when (mediaType) {
        MediaType.PHOTO -> "$algo · $usm · JPEG ${photoConfig.jpegQuality}%"
        MediaType.VIDEO -> "$algo · $usm · $encoder"
        null -> "$algo · $usm · $encoder"
    }
}

fun buildAdvancedTuningUiState(
    mediaType: MediaType?,
    platform: TargetPlatform,
    photoConfig: PhotoAdvancedConfig,
    videoConfig: VideoAdvancedConfig,
    activeSpec: OptimalMediaSpec?,
): AdvancedTuningUiState {
    val tuningMode =
        when (mediaType) {
            MediaType.PHOTO -> MediaTuningMode.PHOTO
            MediaType.VIDEO -> MediaTuningMode.VIDEO
            null -> MediaTuningMode.BOTH
        }
    val preset = platform.preset
    val manualKbps =
        (videoConfig.bitrateMode as? BitrateMode.Manual)?.kbps
            ?: AdvancedTuningUiState.DEFAULT_MANUAL_BITRATE_KBPS
    val maxMbps = preset.videoMaxBitrateKbps / 1_000.0
    val maxMbpsText = String.format(Locale.US, "%.1f", maxMbps)
    val outputRes =
        activeSpec?.let { MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height) }
            ?: "1080 × 1920"

    return AdvancedTuningUiState(
        mediaMode = tuningMode,
        scalingAlgorithm =
            when (photoConfig.scalingAlgorithm) {
                ScalingAlgorithm.LANCZOS3 -> ResamplingOption.LANCZOS_3
                ScalingAlgorithm.BICUBIC -> ResamplingOption.BICUBIC
            },
        sharpnessPercent = photoConfig.sharpnessPercent,
        defaultSharpnessPercent = preset.photoSharpnessPercent,
        photoTargetResolutionPx = photoConfig.targetResolution?.longEdgePx,
        jpegQuality = photoConfig.jpegQuality,
        defaultJpegQuality = preset.photoJpegQuality,
        autoSrgbFix = photoConfig.autoSrgbFix,
        videoResolution =
            when (videoConfig.resolution) {
                VideoResolution.FHD_1080P -> VideoResolutionOption.FHD_1080P
                VideoResolution.HD_720P_ULTRA_SHARP -> VideoResolutionOption.HD_720P_ULTRA_SHARP
            },
        frameRateMode =
            when (videoConfig.frameRateMode) {
                FrameRateMode.LOCK_30 -> FrameRateOption.LOCK_30
                FrameRateMode.FPS_60 -> FrameRateOption.FPS_60
                FrameRateMode.ORIGINAL -> FrameRateOption.ORIGINAL
            },
        encoder =
            when (videoConfig.encoder) {
                VideoEncoder.CPU_LIBX264 -> EncoderOption.CPU_LIBX264
                VideoEncoder.GPU_MEDIACODEC -> EncoderOption.GPU_MEDIACODEC
            },
        isManualBitrate = videoConfig.bitrateMode is BitrateMode.Manual,
        manualBitrateKbps = manualKbps,
        autoBitrateSummary = "Auto Sweet-Spot (Max $maxMbpsText Mbps ${platform.toShortLabel()})",
        hdrToSdr = videoConfig.hdrToSdr,
        autoSplit =
            when (videoConfig.autoSplit) {
                AutoSplitMode.OFF -> AutoSplitOption.OFF
                AutoSplitMode.EVERY_30S -> AutoSplitOption.EVERY_30S
                AutoSplitMode.EVERY_60S -> AutoSplitOption.EVERY_60S
            },
        outputSummaryLine = "Output: $outputRes (Modulo-2 Locked) · JPEG ${photoConfig.jpegQuality}%",
    )
}

fun dispatchAdvancedTuningEvent(
    event: AdvancedTuningEvent,
    platform: TargetPlatform,
    onUpdatePhoto: ((PhotoAdvancedConfig) -> PhotoAdvancedConfig) -> Unit,
    onUpdateVideo: ((VideoAdvancedConfig) -> VideoAdvancedConfig) -> Unit,
) {
    when (event) {
        is AdvancedTuningEvent.SelectScaling,
        is AdvancedTuningEvent.ChangeSharpness,
        AdvancedTuningEvent.ResetSharpness,
        is AdvancedTuningEvent.SelectPhotoResolution,
        is AdvancedTuningEvent.ChangeJpegQuality,
        AdvancedTuningEvent.ResetJpegQuality,
        is AdvancedTuningEvent.ToggleAutoSrgbFix,
        -> dispatchPhotoTuningEvent(event, platform, onUpdatePhoto)

        is AdvancedTuningEvent.SelectVideoResolution,
        is AdvancedTuningEvent.SelectFrameRate,
        is AdvancedTuningEvent.SelectEncoder,
        is AdvancedTuningEvent.ToggleManualBitrate,
        is AdvancedTuningEvent.ChangeManualBitrateKbps,
        is AdvancedTuningEvent.ToggleHdrToSdr,
        is AdvancedTuningEvent.SelectAutoSplit,
        -> dispatchVideoTuningEvent(event, onUpdateVideo)

        AdvancedTuningEvent.ResetAllToOptimal -> {
            onUpdatePhoto { PhotoAdvancedConfig.defaultFor(platform) }
            onUpdateVideo { VideoAdvancedConfig.defaultFor(platform) }
        }
    }
}

private fun dispatchPhotoTuningEvent(
    event: AdvancedTuningEvent,
    platform: TargetPlatform,
    onUpdatePhoto: ((PhotoAdvancedConfig) -> PhotoAdvancedConfig) -> Unit,
) {
    when (event) {
        is AdvancedTuningEvent.SelectScaling ->
            onUpdatePhoto { it.copy(scalingAlgorithm = event.option.toDomain()) }
        is AdvancedTuningEvent.ChangeSharpness ->
            onUpdatePhoto { it.copy(sharpnessPercent = AdvancedTuningUiState.clampSharpness(event.percent)) }
        AdvancedTuningEvent.ResetSharpness ->
            onUpdatePhoto { it.copy(sharpnessPercent = platform.preset.photoSharpnessPercent) }
        is AdvancedTuningEvent.SelectPhotoResolution ->
            onUpdatePhoto { config ->
                val mapped = PhotoTargetResolution.entries.firstOrNull { it.longEdgePx == event.longEdgePx }
                config.copy(targetResolution = mapped)
            }
        is AdvancedTuningEvent.ChangeJpegQuality ->
            onUpdatePhoto { it.copy(jpegQuality = AdvancedTuningUiState.clampJpegQuality(event.quality)) }
        AdvancedTuningEvent.ResetJpegQuality ->
            onUpdatePhoto { it.copy(jpegQuality = platform.preset.photoJpegQuality) }
        is AdvancedTuningEvent.ToggleAutoSrgbFix ->
            onUpdatePhoto { it.copy(autoSrgbFix = event.enabled) }
        else -> Unit
    }
}

private fun dispatchVideoTuningEvent(
    event: AdvancedTuningEvent,
    onUpdateVideo: ((VideoAdvancedConfig) -> VideoAdvancedConfig) -> Unit,
) {
    when (event) {
        is AdvancedTuningEvent.SelectVideoResolution ->
            onUpdateVideo { it.copy(resolution = event.option.toDomain()) }
        is AdvancedTuningEvent.SelectFrameRate ->
            onUpdateVideo { it.copy(frameRateMode = event.option.toDomain()) }
        is AdvancedTuningEvent.SelectEncoder ->
            onUpdateVideo { it.copy(encoder = event.option.toDomain()) }
        is AdvancedTuningEvent.ToggleManualBitrate ->
            onUpdateVideo { it.copy(bitrateMode = resolveManualBitrateMode(event.manual)) }
        is AdvancedTuningEvent.ChangeManualBitrateKbps ->
            onUpdateVideo {
                it.copy(bitrateMode = BitrateMode.Manual(AdvancedTuningUiState.clampManualBitrateKbps(event.kbps)))
            }
        is AdvancedTuningEvent.ToggleHdrToSdr ->
            onUpdateVideo { it.copy(hdrToSdr = event.enabled) }
        is AdvancedTuningEvent.SelectAutoSplit ->
            onUpdateVideo { it.copy(autoSplit = event.option.toDomain()) }
        else -> Unit
    }
}

private fun resolveManualBitrateMode(isManual: Boolean): BitrateMode =
    if (isManual) {
        BitrateMode.Manual(AdvancedTuningUiState.DEFAULT_MANUAL_BITRATE_KBPS)
    } else {
        BitrateMode.AutoSweetSpot
    }

private fun ResamplingOption.toDomain(): ScalingAlgorithm =
    when (this) {
        ResamplingOption.LANCZOS_3 -> ScalingAlgorithm.LANCZOS3
        ResamplingOption.BICUBIC -> ScalingAlgorithm.BICUBIC
    }

private fun VideoResolutionOption.toDomain(): VideoResolution =
    when (this) {
        VideoResolutionOption.FHD_1080P -> VideoResolution.FHD_1080P
        VideoResolutionOption.HD_720P_ULTRA_SHARP -> VideoResolution.HD_720P_ULTRA_SHARP
    }

private fun FrameRateOption.toDomain(): FrameRateMode =
    when (this) {
        FrameRateOption.LOCK_30 -> FrameRateMode.LOCK_30
        FrameRateOption.FPS_60 -> FrameRateMode.FPS_60
        FrameRateOption.ORIGINAL -> FrameRateMode.ORIGINAL
    }

private fun EncoderOption.toDomain(): VideoEncoder =
    when (this) {
        EncoderOption.CPU_LIBX264 -> VideoEncoder.CPU_LIBX264
        EncoderOption.GPU_MEDIACODEC -> VideoEncoder.GPU_MEDIACODEC
    }

private fun AutoSplitOption.toDomain(): AutoSplitMode =
    when (this) {
        AutoSplitOption.OFF -> AutoSplitMode.OFF
        AutoSplitOption.EVERY_30S -> AutoSplitMode.EVERY_30S
        AutoSplitOption.EVERY_60S -> AutoSplitMode.EVERY_60S
    }
