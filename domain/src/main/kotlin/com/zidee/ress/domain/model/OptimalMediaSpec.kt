package com.zidee.ress.domain.model

/** Everything the use case needs to compute a spec for one source file. */
data class OptimizationRequest(
    val source: MediaMetadata,
    val platform: TargetPlatform,
    val aspectMode: CanvasAspectMode,
    val photoConfig: PhotoAdvancedConfig = PhotoAdvancedConfig.defaultFor(platform),
    val videoConfig: VideoAdvancedConfig = VideoAdvancedConfig.defaultFor(platform),
    val crop: CropAdjustment = CropAdjustment.CENTER,
    val blurRadiusPx: Int = CanvasGeometry.DEFAULT_BLUR_RADIUS_PX,
) {
    init {
        require(blurRadiusPx in CanvasGeometry.BLUR_RADIUS_RANGE) {
            "blurRadiusPx must be in ${CanvasGeometry.BLUR_RADIUS_RANGE}"
        }
    }
}

/** How the source maps onto the output canvas. */
sealed interface CanvasGeometry {
    /** Whole source scaled to the output size, no crop, no padding. */
    data object Scale : CanvasGeometry

    /** [sourceRegion] (source pixels) is cut out and scaled to fill the output. */
    data class Crop(val sourceRegion: PixelRect) : CanvasGeometry

    /** Whole source scaled to [foreground] (canvas pixels) over a blurred fill of the canvas. */
    data class BlurBackground(
        val foreground: PixelRect,
        val blurRadiusPx: Int = DEFAULT_BLUR_RADIUS_PX,
    ) : CanvasGeometry {
        init {
            require(blurRadiusPx in BLUR_RADIUS_RANGE) {
                "blurRadiusPx must be in $BLUR_RADIUS_RANGE"
            }
        }
    }

    companion object {
        const val MIN_BLUR_RADIUS_PX: Int = 10
        const val MAX_BLUR_RADIUS_PX: Int = 60
        const val DEFAULT_BLUR_RADIUS_PX: Int = 35
        val BLUR_RADIUS_RANGE: IntRange = MIN_BLUR_RADIUS_PX..MAX_BLUR_RADIUS_PX
    }
}

data class PhotoEncodingSpec(
    val scalingAlgorithm: ScalingAlgorithm,
    val sharpnessPercent: Int,
    /** Unsharp-mask alpha, 0..100% mapped linearly to 0.0..1.5. */
    val unsharpAmount: Double,
    val jpegQuality: Int,
    val convertToSrgb: Boolean,
)

data class VideoEncodingSpec(
    val frameRate: Double,
    val encoder: VideoEncoder,
    val maxBitrateKbps: Int,
    val bufferSizeKbps: Int,
    val audioBitrateKbps: Int,
    val audioSampleRateHz: Int,
    val sharpen: Boolean,
    val applyHdrToSdr: Boolean,
    /** Seconds per segment when Auto-Split is active, otherwise null. */
    val segmentSeconds: Int?,
    /** Planned part durations. A single entry when no split happens; empty if duration is unknown. */
    val segmentDurationsMs: List<Long>,
)

/**
 * Output of [com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase]. All pixel sizes are even.
 */
data class OptimalMediaSpec(
    val platform: TargetPlatform,
    val aspectMode: CanvasAspectMode,
    val mediaType: MediaType,
    val sourceSize: PixelSize,
    val outputSize: PixelSize,
    val geometry: CanvasGeometry,
    val photo: PhotoEncodingSpec?,
    val video: VideoEncodingSpec?,
    /** Rough size guess for UI. For CPU video it is a ceiling, not a prediction. Null if unknown. */
    val estimatedSizeBytes: Long?,
)
