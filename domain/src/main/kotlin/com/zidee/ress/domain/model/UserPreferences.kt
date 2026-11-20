package com.zidee.ress.domain.model

import com.zidee.ress.domain.model.PhotoAdvancedConfig.Companion.SHARPNESS_RANGE

/** Persisted user defaults from Jetpack DataStore. */
data class UserPreferences(
    val lastSelectedPlatform: TargetPlatform = TargetPlatform.WA_STORY,
    val lastSelectedAspectMode: CanvasAspectMode = CanvasAspectMode.BLUR_BG_9_16,
    val preferredVideoEncoder: VideoEncoder = VideoEncoder.CPU_LIBX264,
    val defaultSharpnessPercent: Int? = null,
) {
    init {
        require(defaultSharpnessPercent == null || defaultSharpnessPercent in SHARPNESS_RANGE) {
            "defaultSharpnessPercent must be in $SHARPNESS_RANGE"
        }
    }
}
