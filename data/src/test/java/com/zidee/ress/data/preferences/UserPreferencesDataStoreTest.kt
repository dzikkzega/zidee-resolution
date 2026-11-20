package com.zidee.ress.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.model.VideoEncoder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesDataStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `emits defaults then persists platform, aspect mode, encoder, and sharpness`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    tempFolder.newFile("zidee_preferences.preferences_pb")
                }
            val repo = UserPreferencesDataStore(dataStore)

            repo.observePreferences().test {
                assertEquals(UserPreferences(), awaitItem())

                assertTrue(repo.setLastSelection(TargetPlatform.IG_POST, CanvasAspectMode.CROP_9_16) is AppResult.Success)
                val afterSelection = awaitItem()
                assertEquals(TargetPlatform.IG_POST, afterSelection.lastSelectedPlatform)
                assertEquals(CanvasAspectMode.CROP_9_16, afterSelection.lastSelectedAspectMode)

                assertTrue(repo.setPreferredEncoder(VideoEncoder.GPU_MEDIACODEC) is AppResult.Success)
                assertEquals(VideoEncoder.GPU_MEDIACODEC, awaitItem().preferredVideoEncoder)

                assertTrue(repo.setDefaultSharpnessPercent(32) is AppResult.Success)
                assertEquals(32, awaitItem().defaultSharpnessPercent)

                assertTrue(repo.setDefaultSharpnessPercent(null) is AppResult.Success)
                assertNull(awaitItem().defaultSharpnessPercent)
            }
        }

    @Test
    fun `falls back safely when stored enum names are unrecognized`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    tempFolder.newFile("corrupt.preferences_pb")
                }
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("last_selected_platform")] = "UNKNOWN_PLATFORM"
                prefs[stringPreferencesKey("last_selected_aspect_mode")] = "UNKNOWN_MODE"
                prefs[stringPreferencesKey("preferred_video_encoder")] = "UNKNOWN_ENCODER"
            }
            val repo = UserPreferencesDataStore(dataStore)

            repo.observePreferences().test {
                assertEquals(UserPreferences(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
