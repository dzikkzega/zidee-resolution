package com.zidee.ress.domain.repository

import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.model.VideoEncoder
import kotlinx.coroutines.flow.Flow

/** Extracts dimensions, EXIF rotation, duration, FPS, and HDR/wide-gamut flags from a URI. */
interface MediaMetadataRepository {
    suspend fun inspect(uri: MediaUri): AppResult<MediaMetadata>
}

/** Persisted UI defaults (last platform, aspect ratio, preferred encoder, sharpness). */
interface UserPreferencesRepository {
    fun observePreferences(): Flow<UserPreferences>

    suspend fun setLastSelection(
        platform: TargetPlatform,
        aspectMode: CanvasAspectMode,
    ): AppResult<Unit>

    suspend fun setPreferredEncoder(encoder: VideoEncoder): AppResult<Unit>

    suspend fun setDefaultSharpnessPercent(percent: Int?): AppResult<Unit>
}
