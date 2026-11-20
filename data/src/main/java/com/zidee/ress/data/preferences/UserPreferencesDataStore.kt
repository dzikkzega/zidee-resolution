package com.zidee.ress.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.PhotoAdvancedConfig.Companion.SHARPNESS_RANGE
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.zideePreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "zidee_preferences",
)

class UserPreferencesDataStore(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {
    constructor(context: Context) : this(context.applicationContext.zideePreferencesStore)

    override fun observePreferences(): Flow<UserPreferences> =
        dataStore.data.map { prefs ->
            val default = UserPreferences()
            val platform = prefs[KEY_LAST_PLATFORM]?.let { parseEnum<TargetPlatform>(it) } ?: default.lastSelectedPlatform
            val aspectMode = prefs[KEY_LAST_ASPECT_MODE]?.let { parseEnum<CanvasAspectMode>(it) } ?: default.lastSelectedAspectMode
            val encoder = prefs[KEY_PREFERRED_ENCODER]?.let { parseEnum<VideoEncoder>(it) } ?: default.preferredVideoEncoder
            val sharpness = prefs[KEY_DEFAULT_SHARPNESS]?.takeIf { it in SHARPNESS_RANGE }
            UserPreferences(
                lastSelectedPlatform = platform,
                lastSelectedAspectMode = aspectMode,
                preferredVideoEncoder = encoder,
                defaultSharpnessPercent = sharpness,
            )
        }

    override suspend fun setLastSelection(
        platform: TargetPlatform,
        aspectMode: CanvasAspectMode,
    ): AppResult<Unit> =
        editSafely { prefs ->
            prefs[KEY_LAST_PLATFORM] = platform.name
            prefs[KEY_LAST_ASPECT_MODE] = aspectMode.name
        }

    override suspend fun setPreferredEncoder(encoder: VideoEncoder): AppResult<Unit> =
        editSafely { prefs ->
            prefs[KEY_PREFERRED_ENCODER] = encoder.name
        }

    override suspend fun setDefaultSharpnessPercent(percent: Int?): AppResult<Unit> =
        editSafely { prefs ->
            if (percent == null) {
                prefs.remove(KEY_DEFAULT_SHARPNESS)
            } else {
                require(percent in SHARPNESS_RANGE) { "Sharpness must be in $SHARPNESS_RANGE" }
                prefs[KEY_DEFAULT_SHARPNESS] = percent
            }
        }

    private suspend inline fun editSafely(
        crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): AppResult<Unit> =
        try {
            dataStore.edit { prefs -> block(prefs) }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppResult.Failure(AppError.StorageWrite(t.message ?: "Failed persisting user preferences", t))
        }

    private inline fun <reified E : Enum<E>> parseEnum(raw: String): E? = enumValues<E>().firstOrNull { it.name == raw }

    private companion object {
        val KEY_LAST_PLATFORM = stringPreferencesKey("last_selected_platform")
        val KEY_LAST_ASPECT_MODE = stringPreferencesKey("last_selected_aspect_mode")
        val KEY_PREFERRED_ENCODER = stringPreferencesKey("preferred_video_encoder")
        val KEY_DEFAULT_SHARPNESS = intPreferencesKey("default_sharpness_percent")
    }
}
