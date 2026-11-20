package com.zidee.ress.designsystem.component

import androidx.compose.runtime.Immutable
import java.util.Locale

enum class MediaTuningMode {
    PHOTO,
    VIDEO,
    BOTH,
}

enum class ResamplingOption {
    LANCZOS_3,
    BICUBIC,
}

enum class VideoResolutionOption {
    FHD_1080P,
    HD_720P_ULTRA_SHARP,
}

enum class FrameRateOption {
    LOCK_30,
    FPS_60,
    ORIGINAL,
}

enum class EncoderOption {
    CPU_LIBX264,
    GPU_MEDIACODEC,
}

enum class AutoSplitOption {
    OFF,
    EVERY_30S,
    EVERY_60S,
}

@Immutable
data class AdvancedTuningUiState(
    val mediaMode: MediaTuningMode = MediaTuningMode.BOTH,
    val scalingAlgorithm: ResamplingOption = ResamplingOption.LANCZOS_3,
    val sharpnessPercent: Int = DEFAULT_SHARPNESS_PERCENT,
    val defaultSharpnessPercent: Int = DEFAULT_SHARPNESS_PERCENT,
    val photoTargetResolutionPx: Int? = null,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    val defaultJpegQuality: Int = DEFAULT_JPEG_QUALITY,
    val autoSrgbFix: Boolean = true,
    val videoResolution: VideoResolutionOption = VideoResolutionOption.FHD_1080P,
    val frameRateMode: FrameRateOption = FrameRateOption.LOCK_30,
    val encoder: EncoderOption = EncoderOption.CPU_LIBX264,
    val isManualBitrate: Boolean = false,
    val manualBitrateKbps: Int = DEFAULT_MANUAL_BITRATE_KBPS,
    val autoBitrateSummary: String = "Auto (Max 2.4 Mbps WA / 4.0 Mbps IG)",
    val hdrToSdr: Boolean = true,
    val autoSplit: AutoSplitOption = AutoSplitOption.EVERY_30S,
    val outputSummaryLine: String = "Output: 1080×1920 (Modulo-2 Locked) · JPEG 94%",
) {
    val isSharpnessModified: Boolean
        get() = sharpnessPercent != defaultSharpnessPercent

    val isJpegQualityModified: Boolean
        get() = jpegQuality != defaultJpegQuality

    fun formatBitrateMbps(): String = String.format(Locale.US, "%.1f Mbps", manualBitrateKbps / KBPS_PER_MBPS)

    companion object {
        const val MIN_SHARPNESS_PERCENT: Int = 0
        const val MAX_SHARPNESS_PERCENT: Int = 100
        const val DEFAULT_SHARPNESS_PERCENT: Int = 25

        const val MIN_JPEG_QUALITY: Int = 80
        const val MAX_JPEG_QUALITY: Int = 100
        const val DEFAULT_JPEG_QUALITY: Int = 94

        const val MIN_BITRATE_KBPS: Int = 1_500
        const val MAX_BITRATE_KBPS: Int = 12_000
        const val DEFAULT_MANUAL_BITRATE_KBPS: Int = 3_500
        private const val KBPS_PER_MBPS: Float = 1_000f

        val PHOTO_TARGET_RESOLUTIONS: List<Int?> = listOf(null, 1080, 1600, 2048)

        fun clampSharpness(percent: Int): Int = percent.coerceIn(MIN_SHARPNESS_PERCENT, MAX_SHARPNESS_PERCENT)

        fun clampJpegQuality(quality: Int): Int = quality.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY)

        fun clampManualBitrateKbps(kbps: Int): Int = kbps.coerceIn(MIN_BITRATE_KBPS, MAX_BITRATE_KBPS)
    }
}

sealed interface AdvancedTuningEvent {
    data class SelectScaling(val option: ResamplingOption) : AdvancedTuningEvent

    data class ChangeSharpness(val percent: Int) : AdvancedTuningEvent

    data object ResetSharpness : AdvancedTuningEvent

    data class SelectPhotoResolution(val longEdgePx: Int?) : AdvancedTuningEvent

    data class ChangeJpegQuality(val quality: Int) : AdvancedTuningEvent

    data object ResetJpegQuality : AdvancedTuningEvent

    data class ToggleAutoSrgbFix(val enabled: Boolean) : AdvancedTuningEvent

    data class SelectVideoResolution(val option: VideoResolutionOption) : AdvancedTuningEvent

    data class SelectFrameRate(val option: FrameRateOption) : AdvancedTuningEvent

    data class SelectEncoder(val option: EncoderOption) : AdvancedTuningEvent

    data class ToggleManualBitrate(val manual: Boolean) : AdvancedTuningEvent

    data class ChangeManualBitrateKbps(val kbps: Int) : AdvancedTuningEvent

    data class ToggleHdrToSdr(val enabled: Boolean) : AdvancedTuningEvent

    data class SelectAutoSplit(val option: AutoSplitOption) : AdvancedTuningEvent

    data object ResetAllToOptimal : AdvancedTuningEvent
}
