package com.zidee.ress.presentation.quickshare

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.zidee.ress.core.result.AppError
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.PhotoTargetResolution
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.model.VideoResolution
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import com.zidee.ress.presentation.fakes.FakeMediaMetadataRepository
import com.zidee.ress.presentation.fakes.FakeMediaOptimizerEngine
import com.zidee.ress.presentation.fakes.FakeMediaStorageRepository
import com.zidee.ress.presentation.fakes.FakeOptimizationHistoryRepository
import com.zidee.ress.presentation.fakes.FakeUserPreferencesRepository
import com.zidee.ress.presentation.fakes.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickShareViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `process and share saves multi-part story video to ZideeResolution first then emits direct share`() =
        runTest(mainDispatcherRule.dispatcher) {
            val uri = MediaUri("content://gallery/video/1")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(uri, MediaType.VIDEO, 1920, 1080, 24_000_000L, durationMs = 75_000L, frameRate = 60.0))
                }
            val storage = FakeMediaStorageRepository()
            val history = FakeOptimizationHistoryRepository()
            val prefs =
                FakeUserPreferencesRepository(
                    UserPreferences(
                        lastSelectedPlatform = TargetPlatform.WA_STORY,
                        lastSelectedAspectMode = CanvasAspectMode.ORIGINAL,
                    ),
                )
            val calc = CalculateOptimalSpecUseCase()
            val vm =
                QuickShareViewModel(
                    calculateOptimalSpec = calc,
                    optimizeMedia = OptimizeMediaUseCase(calc, FakeMediaOptimizerEngine(), storage, history),
                    metadataRepository = metadataRepo,
                    preferencesRepository = prefs,
                    savedStateHandle = SavedStateHandle(mapOf(QuickShareViewModel.KEY_SOURCE_URI to uri.value)),
                )

            vm.state.test {
                advanceUntilIdle()
                val ready = expectMostRecentItem()
                assertEquals(TargetPlatform.WA_STORY, ready.selectedPlatform)
                assertFalse(ready.isInspecting)

                vm.onEvent(QuickShareUiEvent.SelectAspectMode(CanvasAspectMode.BLUR_BG_9_16))
                val updatedMode = awaitItem()
                assertEquals(CanvasAspectMode.BLUR_BG_9_16, updatedMode.selectedAspectMode)
                assertEquals(listOf(30_000L, 30_000L, 15_000L), updatedMode.activeSpec?.video?.segmentDurationsMs)

                vm.onEvent(QuickShareUiEvent.ToggleAdvanced)
                assertTrue(awaitItem().isAdvancedExpanded)

                vm.onEvent(
                    QuickShareUiEvent.UpdateVideoConfig {
                        it.copy(resolution = VideoResolution.HD_720P_ULTRA_SHARP)
                    },
                )
                val updatedVideo = awaitItem()
                assertEquals(VideoResolution.HD_720P_ULTRA_SHARP, updatedVideo.videoConfig.resolution)
                assertEquals(PixelSize(1080, 1920), updatedVideo.activeSpec?.outputSize)

                vm.onEvent(QuickShareUiEvent.ClickSaveAndShare())
                advanceUntilIdle()

                val completed = expectMostRecentItem()
                assertFalse(completed.isProcessing)
                assertEquals(3, completed.completedItems.size)
            }

            assertEquals(3, storage.savedFiles.size)
            assertEquals(3, history.itemsFlow.value.size)

            vm.effect.test {
                val effect = awaitItem() as QuickShareUiEffect.LaunchTargetAppShare
                assertEquals("com.whatsapp", effect.payload.targetPackage)
                assertEquals("video/mp4", effect.payload.mimeType)
                assertEquals(3, effect.payload.uris.size)
            }
        }

    @Test
    fun `partial quick-share batch preserves saved item and exposes failure summary`() =
        runTest(mainDispatcherRule.dispatcher) {
            val okUri = MediaUri("content://gallery/photo/qs_ok")
            val failUri = MediaUri("content://gallery/video/qs_fail")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(okUri, MediaType.PHOTO, 3000, 2000, 2_000_000L))
                    putSuccess(MediaMetadata(failUri, MediaType.VIDEO, 1920, 1080, 10_000_000L, durationMs = 10_000L))
                }
            val engine =
                FakeMediaOptimizerEngine().apply {
                    failForUris[failUri.value] = IllegalStateException("Muxer failed")
                }
            val storage = FakeMediaStorageRepository()
            val history = FakeOptimizationHistoryRepository()
            val calc = CalculateOptimalSpecUseCase()
            val vm =
                QuickShareViewModel(
                    calculateOptimalSpec = calc,
                    optimizeMedia = OptimizeMediaUseCase(calc, engine, storage, history),
                    metadataRepository = metadataRepo,
                    preferencesRepository = FakeUserPreferencesRepository(),
                    savedStateHandle = SavedStateHandle(mapOf(QuickShareViewModel.KEY_SOURCE_URIS to listOf(okUri.value, failUri.value))),
                )
            advanceUntilIdle()

            vm.onEvent(QuickShareUiEvent.ClickSaveAndShare())
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isProcessing)
            assertEquals(1, state.completedItems.size)
            assertEquals(1, storage.savedFiles.size)
            assertEquals(1, history.itemsFlow.value.size)
            assertTrue(state.errorMessage?.contains("1 file tersimpan") == true)
            assertTrue(state.errorMessage?.contains("1 gagal") == true)

            vm.effect.test {
                expectNoEvents()
            }
        }

    @Test
    fun `click save only stores permanently without emitting share intent`() =
        runTest(mainDispatcherRule.dispatcher) {
            val uri = MediaUri("content://gallery/photo/77")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(uri, MediaType.PHOTO, 4000, 3000, 4_000_000L))
                }
            val storage = FakeMediaStorageRepository()
            val history = FakeOptimizationHistoryRepository()
            val calc = CalculateOptimalSpecUseCase()
            val vm =
                QuickShareViewModel(
                    calculateOptimalSpec = calc,
                    optimizeMedia = OptimizeMediaUseCase(calc, FakeMediaOptimizerEngine(), storage, history),
                    metadataRepository = metadataRepo,
                    preferencesRepository = FakeUserPreferencesRepository(),
                )

            vm.onEvent(QuickShareUiEvent.LoadSources(listOf(uri)))
            advanceUntilIdle()
            vm.onEvent(QuickShareUiEvent.SelectAspectMode(CanvasAspectMode.ORIGINAL))
            vm.onEvent(QuickShareUiEvent.SelectPlatform(TargetPlatform.WA_CHAT))
            vm.onEvent(
                QuickShareUiEvent.UpdatePhotoConfig {
                    it.copy(targetResolution = PhotoTargetResolution.PX_1080)
                },
            )
            assertEquals(PixelSize(1080, 810), vm.state.value.activeSpec?.outputSize)

            vm.onEvent(QuickShareUiEvent.ClickSaveOnly)
            advanceUntilIdle()

            vm.effect.test {
                val effect = awaitItem()
                assertTrue(effect is QuickShareUiEffect.SavedOnlyCompleted)
                assertEquals(1, (effect as QuickShareUiEffect.SavedOnlyCompleted).items.size)
            }
            assertEquals(1, storage.savedFiles.size)
        }

    @Test
    fun `click cancel aborts processing and emits DismissSheet`() =
        runTest(mainDispatcherRule.dispatcher) {
            val uri = MediaUri("content://gallery/video/9")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(uri, MediaType.VIDEO, 1080, 1920, 8_000_000L, durationMs = 20_000L))
                }
            val engine = FakeMediaOptimizerEngine().apply { renderDelayMs = 4_000L }
            val calc = CalculateOptimalSpecUseCase()
            val vm =
                QuickShareViewModel(
                    calculateOptimalSpec = calc,
                    optimizeMedia = OptimizeMediaUseCase(calc, engine, FakeMediaStorageRepository(), FakeOptimizationHistoryRepository()),
                    metadataRepository = metadataRepo,
                    preferencesRepository = FakeUserPreferencesRepository(),
                    savedStateHandle = SavedStateHandle(mapOf(QuickShareViewModel.KEY_SOURCE_URI to uri.value)),
                )
            advanceUntilIdle()

            vm.onEvent(QuickShareUiEvent.ClickSaveAndShare())
            advanceTimeBy(500L)
            assertTrue(vm.state.value.isProcessing)

            vm.onEvent(QuickShareUiEvent.ClickCancel)
            advanceUntilIdle()

            assertFalse(vm.state.value.isProcessing)
            vm.effect.test {
                assertEquals(QuickShareUiEffect.DismissSheet, awaitItem())
            }
        }

    @Test
    fun `unsupported incoming media, empty source, and save failure expose dismissible errors`() =
        runTest(mainDispatcherRule.dispatcher) {
            val badUri = MediaUri("content://gallery/doc/99")
            val goodUri = MediaUri("content://gallery/photo/22")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putFailure(badUri, AppError.UnsupportedMedia("application/pdf"))
                    putSuccess(MediaMetadata(goodUri, MediaType.PHOTO, 2000, 2000, 1_000_000L))
                }
            val storage = FakeMediaStorageRepository().apply { failSave = AppError.InsufficientStorage(64_000_000L) }
            val calc = CalculateOptimalSpecUseCase()
            val vm =
                QuickShareViewModel(
                    calculateOptimalSpec = calc,
                    optimizeMedia =
                        OptimizeMediaUseCase(
                            calc,
                            FakeMediaOptimizerEngine(),
                            storage,
                            FakeOptimizationHistoryRepository(),
                        ),
                    metadataRepository = metadataRepo,
                    preferencesRepository = FakeUserPreferencesRepository(),
                    savedStateHandle = SavedStateHandle(mapOf(QuickShareViewModel.KEY_SOURCE_URIS to listOf(badUri.value))),
                )
            advanceUntilIdle()

            assertFalse(vm.state.value.canSubmit)
            assertNotNull(vm.state.value.errorMessage)

            vm.onEvent(QuickShareUiEvent.DismissError)
            assertNull(vm.state.value.errorMessage)

            vm.onEvent(QuickShareUiEvent.LoadSources(emptyList()))
            assertNotNull(vm.state.value.errorMessage)

            vm.onEvent(QuickShareUiEvent.LoadSources(listOf(goodUri)))
            advanceUntilIdle()
            vm.onEvent(QuickShareUiEvent.ClickSaveOnly)
            advanceUntilIdle()

            assertFalse(vm.state.value.isProcessing)
            assertTrue(vm.state.value.errorMessage?.contains("61 MB") == true)
        }
}
