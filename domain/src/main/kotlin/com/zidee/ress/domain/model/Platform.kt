package com.zidee.ress.domain.model

/** Destination the media is tuned for. Order matches the preset bar in the UI. */
enum class TargetPlatform {
    WA_STORY,
    WA_CHAT,
    IG_STORY,
    IG_POST,
    IG_CHAT,
}

/** The three canvas modes every platform supports. */
enum class CanvasAspectMode {
    /** Keep source aspect ratio, downscale to the platform's pixel budget. */
    ORIGINAL,

    /** Absolute 9:16 canvas, full source centred over a Gaussian-blurred copy of itself. */
    BLUR_BG_9_16,

    /** Absolute 9:16 canvas, source cropped (centre or user pan/zoom). */
    CROP_9_16,
}

enum class MediaType { PHOTO, VIDEO }

/** Permanent gallery album. Every output lands here, from both Studio and Quick Share. */
object ZideeAlbum {
    const val NAME: String = "ZideeResolution"
    const val PHOTO_RELATIVE_PATH: String = "Pictures/$NAME"
    const val VIDEO_RELATIVE_PATH: String = "Movies/$NAME"

    fun relativePathFor(type: MediaType): String =
        when (type) {
            MediaType.PHOTO -> PHOTO_RELATIVE_PATH
            MediaType.VIDEO -> VIDEO_RELATIVE_PATH
        }
}
