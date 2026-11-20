package com.zidee.ress.domain.model

enum class ScalingAlgorithm { LANCZOS3, BICUBIC }

/** Long-edge cap selectable in Advanced Pro (photo). */
enum class PhotoTargetResolution(val longEdgePx: Int) {
    PX_1080(1080),
    PX_1600(1600),
    PX_2048(2048),
}

/**
 * Advanced Pro photo parameters.
 *
 * [targetResolution] = null means "use the platform preset". When set, it replaces the
 * long-edge cap for [CanvasAspectMode.ORIGINAL] on chat platforms only; story canvases and
 * the IG feed width lock are fixed by the platform and ignore it.
 */
data class PhotoAdvancedConfig(
    val scalingAlgorithm: ScalingAlgorithm = ScalingAlgorithm.LANCZOS3,
    val sharpnessPercent: Int = DEFAULT_SHARPNESS_PERCENT,
    val targetResolution: PhotoTargetResolution? = null,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    val autoSrgbFix: Boolean = true,
) {
    init {
        require(sharpnessPercent in SHARPNESS_RANGE) { "sharpnessPercent must be in $SHARPNESS_RANGE" }
        require(jpegQuality in JPEG_QUALITY_RANGE) { "jpegQuality must be in $JPEG_QUALITY_RANGE" }
    }

    companion object {
        val SHARPNESS_RANGE: IntRange = 0..100
        val JPEG_QUALITY_RANGE: IntRange = 80..100
        const val DEFAULT_SHARPNESS_PERCENT: Int = 25
        const val DEFAULT_JPEG_QUALITY: Int = 94

        /** Preset values from the PRD sweet-spot matrix. */
        fun defaultFor(platform: TargetPlatform): PhotoAdvancedConfig {
            val preset = platform.preset
            return PhotoAdvancedConfig(
                sharpnessPercent = preset.photoSharpnessPercent,
                jpegQuality = preset.photoJpegQuality,
            )
        }
    }
}

enum class VideoResolution {
    FHD_1080P,

    /** 720p keeps more detail per bit at WA Story's low bitrate ceiling. */
    HD_720P_ULTRA_SHARP,
}

enum class FrameRateMode { LOCK_30, FPS_60, ORIGINAL }

enum class VideoEncoder {
    /** libx264, CRF + maxrate. Slowest, sharpest. */
    CPU_LIBX264,

    /** h264_mediacodec hardware encoder. Fast, VBR. */
    GPU_MEDIACODEC,
}

sealed interface BitrateMode {
    data object AutoSweetSpot : BitrateMode

    data class Manual(val kbps: Int) : BitrateMode {
        init {
            require(kbps in RANGE_KBPS) { "Manual bitrate must be in $RANGE_KBPS kbps" }
        }

        companion object {
            val RANGE_KBPS: IntRange = 1_500..12_000
        }
    }
}

enum class AutoSplitMode(val segmentSeconds: Int?) {
    OFF(null),
    EVERY_30S(30),
    EVERY_60S(60),
}

data class VideoAdvancedConfig(
    val resolution: VideoResolution = VideoResolution.FHD_1080P,
    val frameRateMode: FrameRateMode = FrameRateMode.LOCK_30,
    val encoder: VideoEncoder = VideoEncoder.CPU_LIBX264,
    val bitrateMode: BitrateMode = BitrateMode.AutoSweetSpot,
    val antiBlurSharpening: Boolean = true,
    val hdrToSdr: Boolean = true,
    val autoSplit: AutoSplitMode = AutoSplitMode.OFF,
) {
    companion object {
        fun defaultFor(platform: TargetPlatform): VideoAdvancedConfig = VideoAdvancedConfig(autoSplit = platform.preset.defaultAutoSplit)
    }
}
