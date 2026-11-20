package com.zidee.ress.domain.model

/** Abstract content URI. Keeps `android.net.Uri` out of the domain layer. */
@JvmInline
value class MediaUri(val value: String) {
    init {
        require(value.isNotBlank()) { "MediaUri must not be blank" }
    }

    override fun toString(): String = value
}

data class PixelSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "PixelSize must be positive, was ${width}x$height" }
    }

    val isPortrait: Boolean get() = height > width
    val aspectRatio: Double get() = width.toDouble() / height
    val pixelCount: Long get() = width.toLong() * height

    override fun toString(): String = "${width}x$height"
}

/** Axis-aligned rectangle in integer pixels. [left]/[top] are inclusive offsets. */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    init {
        require(left >= 0 && top >= 0) { "PixelRect offset must be non-negative" }
        require(width > 0 && height > 0) { "PixelRect size must be positive" }
    }

    val size: PixelSize get() = PixelSize(width, height)
}

/**
 * What the reader layer (EXIF / MediaMetadataRetriever) knows about a source file.
 *
 * [width]/[height] are the *stored* dimensions; [rotationDegrees] is applied to get
 * the displayed size, which is what all geometry is computed against.
 */
data class MediaMetadata(
    val uri: MediaUri,
    val mediaType: MediaType,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val rotationDegrees: Int = 0,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val frameRate: Double? = null,
    val isHdr: Boolean = false,
    val isWideGamut: Boolean = false,
) {
    init {
        require(width > 0 && height > 0) { "Source dimensions must be positive, was ${width}x$height" }
        require(sizeBytes >= 0) { "sizeBytes must be non-negative" }
        require(rotationDegrees % 90 == 0) { "rotationDegrees must be a multiple of 90, was $rotationDegrees" }
        require(durationMs == null || durationMs >= 0) { "durationMs must be non-negative" }
        require(frameRate == null || frameRate > 0.0) { "frameRate must be positive" }
    }

    val displaySize: PixelSize
        get() =
            if (((rotationDegrees % 360) + 360) % 360 % 180 == 0) {
                PixelSize(width, height)
            } else {
                PixelSize(height, width)
            }
}

/**
 * User pan & zoom for [CanvasAspectMode.CROP_9_16].
 *
 * [panX]/[panY] are fractions of the *free travel* (0 = left/top edge, 1 = right/bottom,
 * 0.5 = centred). [zoom] >= 1 shrinks the crop window.
 */
data class CropAdjustment(
    val panX: Double = 0.5,
    val panY: Double = 0.5,
    val zoom: Double = 1.0,
) {
    init {
        require(panX in 0.0..1.0 && panY in 0.0..1.0) { "pan must be within 0..1" }
        require(zoom in 1.0..MAX_ZOOM) { "zoom must be within 1..$MAX_ZOOM" }
    }

    companion object {
        const val MAX_ZOOM: Double = 5.0
        val CENTER: CropAdjustment = CropAdjustment()
    }
}
