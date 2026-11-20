package com.zidee.ress.presentation.studio

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.zidee.ress.core.result.AppError
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.CropAdjustment
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.PhotoTargetResolution
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.model.VideoEncoder
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
class StudioEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `hydrates saved preferences, inspects source, and updates spec on platform and photo config changes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val uri = MediaUri("content://gallery/photo/10")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(uri, MediaType.PHOTO, 4000, 3000, 5_000_000L))
                }
            val prefsRepo =
                FakeUserPreferencesRepository(
                    UserPreferences(
                        lastSelectedPlatform = TargetPlatform.IG_STORY,
                        lastSelectedAspectMode = CanvasAspectMode.BLUR_BG_9_16,
                        preferredVideoEncoder = VideoEncoder.GPU_MEDIACODEC,
                        defaultSharpnessPercent = 35,
                    ),
                )
            val vm =
                createViewModel(
                    metadataRepo = metadataRepo,
                    prefsRepo = prefsRepo,
                    handle = SavedStateHandle(mapOf(StudioEditorViewModel.KEY_SOURCE_URI to uri.value)),
                )

            advanceUntilIdle()

            val loaded = vm.state.value
            assertEquals(TargetPlatform.IG_STORY, loaded.selectedPlatform)
            assertEquals(CanvasAspectMode.BLUR_BG_9_16, loaded.selectedAspectMode)
            assertEquals(35, loaded.photoConfig.sharpnessPercent)
            assertEquals(VideoEncoder.GPU_MEDIACODEC, loaded.videoConfig.encoder)
            assertEquals(PixelSize(1080, 1920), loaded.activeSpec?.outputSize)

            vm.onEvent(StudioEditorUiEvent.ToggleAdvancedPanel)
            assertTrue(vm.state.value.isAdvancedExpanded)

            vm.onEvent(StudioEditorUiEvent.SelectAspectMode(CanvasAspectMode.ORIGINAL))
            vm.onEvent(StudioEditorUiEvent.SelectPlatform(TargetPlatform.WA_CHAT))
            vm.onEvent(
                StudioEditorUiEvent.UpdatePhotoConfig {
                    it.copy(targetResolution = PhotoTargetResolution.PX_1080)
                },
            )
            assertEquals(TargetPlatform.WA_CHAT, vm.state.value.selectedPlatform)
            assertEquals(PixelSize(1080, 810), vm.state.value.activeSpec?.outputSize)
        }

    @Test
    fun `updating blur radius clamps to 10 to 60 px and passes through to rendered spec`() =
        runTest(mainDispatcherRule.dispatcher) {
            val uri = MediaUri("content://gallery/photo/blur")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(uri, MediaType.PHOTO, 4000, 3000, 4_000_000L))
                }
            val engine = FakeMediaOptimizerEngine()
            val vm = createViewModel(metadataRepo = metadataRepo, engine = engine)

            vm.onEvent(StudioEditorUiEvent.LoadSources(listOf(uri), TargetPlatform.IG_STORY))
            advanceUntilIdle()
            vm.onEvent(StudioEditorUiEvent.SelectAspectMode(CanvasAspectMode.BLUR_BG_9_16))

            vm.onEvent(StudioEditorUiEvent.UpdateBlurRadius(5))
            assertEquals(10, vm.state.value.blurRadiusPx)
            assertEquals(10, (vm.state.value.activeSpec?.geometry as CanvasGeometry.BlurBackground).blurRadiusPx)

            vm.onEvent(StudioEditorUiEvent.UpdateBlurRadius(99))
            assertEquals(60, vm.state.value.blurRadiusPx)
            assertEquals(60, (vm.state.value.activeSpec?.geometry as CanvasGeometry.BlurBackground).blurRadiusPx)

            vm.onEvent(StudioEditorUiEvent.UpdateBlurRadius(48))
            vm.onEvent(StudioEditorUiEvent.SaveToGallery)
            advanceUntilIdle()

            val renderedGeometry = engine.lastSpec?.geometry as? CanvasGeometry.BlurBackground
            assertNotNull(renderedGeometry)
            assertEquals(48, renderedGeometry?.blurRadiusPx)
        }

    @Test
    fun `interactive crop pan, split slider, and batch export save permanently to ZideeResolution`() =
        runTest(mainDispatcherRule.dispatcher) {
            val photoUri = MediaUri("content://gallery/photo/1")
            val videoUri = MediaUri("content://gallery/video/2")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(photoUri, MediaType.PHOTO, 4000, 3000, 3_000_000L))
                    putSuccess(MediaMetadata(videoUri, MediaType.VIDEO, 1920, 1080, 12_000_000L, durationMs = 20_000L, frameRate = 60.0))
                }
            val storage = FakeMediaStorageRepository()
            val history = FakeOptimizationHistoryRepository()
            val prefs = FakeUserPreferencesRepository()
            val vm = createViewModel(metadataRepo = metadataRepo, storage = storage, history = history, prefsRepo = prefs)

            vm.onEvent(StudioEditorUiEvent.LoadSources(listOf(photoUri, videoUri), TargetPlatform.WA_CHAT))
            advanceUntilIdle()

            vm.onEvent(StudioEditorUiEvent.SelectAspectMode(CanvasAspectMode.CROP_9_16))
            vm.onEvent(StudioEditorUiEvent.UpdateCrop(CropAdjustment(panX = 1.0, panY = 0.5, zoom = 2.0)))
            vm.onEvent(StudioEditorUiEvent.UpdateSplitFraction(1.4f))
            vm.onEvent(StudioEditorUiEvent.SelectQueueIndex(1))
            vm.onEvent(
                StudioEditorUiEvent.UpdateVideoConfig {
                    it.copy(resolution = VideoResolution.HD_720P_ULTRA_SHARP)
                },
            )

            val beforeSave = vm.state.value
            assertEquals(1.0f, beforeSave.splitFraction, 0.0f)
            assertTrue(beforeSave.activeSpec?.geometry is CanvasGeometry.Crop)
            assertEquals(VideoResolution.HD_720P_ULTRA_SHARP, beforeSave.videoConfig.resolution)
            assertEquals(PixelSize(1080, 1920), beforeSave.activeSpec?.outputSize)
            assertTrue(beforeSave.canExport)

            vm.effect.test {
                vm.onEvent(StudioEditorUiEvent.SaveAndShare())
                advanceUntilIdle()

                val shareEffect = awaitItem() as StudioEditorUiEffect.LaunchShare
                assertEquals(2, shareEffect.payload.uris.size)
                assertEquals("com.whatsapp", shareEffect.payload.targetPackage)
                assertEquals("*/*", shareEffect.payload.mimeType)
            }

            assertEquals(2, storage.savedFiles.size)
            assertEquals(2, history.itemsFlow.value.size)
            assertEquals(TargetPlatform.WA_CHAT to CanvasAspectMode.CROP_9_16, prefs.lastSelectionSaved)
            assertTrue(vm.state.value.processingState is StudioProcessingState.Completed)

            vm.onEvent(StudioEditorUiEvent.DismissCompleted)
            assertEquals(StudioProcessingState.Idle, vm.state.value.processingState)
        }

    @Test
    fun `partial batch with one success and one failure reports saved item and failure explicitly`() =
        runTest(mainDispatcherRule.dispatcher) {
            val okPhotoUri = MediaUri("content://gallery/photo/ok_batch")
            val failingVideoUri = MediaUri("content://gallery/video/fail_batch")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(okPhotoUri, MediaType.PHOTO, 4000, 3000, 3_000_000L))
                    putSuccess(MediaMetadata(failingVideoUri, MediaType.VIDEO, 1920, 1080, 12_000_000L, durationMs = 15_000L))
                }
            val engine =
                FakeMediaOptimizerEngine().apply {
                    failForUris[failingVideoUri.value] = IllegalStateException("Corrupt video stream")
                }
            val storage = FakeMediaStorageRepository()
            val history = FakeOptimizationHistoryRepository()
            val vm = createViewModel(metadataRepo = metadataRepo, engine = engine, storage = storage, history = history)

            vm.onEvent(StudioEditorUiEvent.LoadSources(listOf(okPhotoUri, failingVideoUri), TargetPlatform.WA_STORY))
            advanceUntilIdle()

            val observedStates = mutableListOf<StudioProcessingState>()
            vm.state.test {
                observedStates += awaitItem().processingState
                vm.onEvent(StudioEditorUiEvent.SaveAndShare())
                advanceUntilIdle()
                while (true) {
                    val next = awaitItem().processingState
                    observedStates += next
                    if (next is StudioProcessingState.PartialSuccess) break
                }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(StudioProcessingState.Idle, observedStates.first())
            assertTrue(observedStates.any { it is StudioProcessingState.Running })
            val partial = observedStates.last() as StudioProcessingState.PartialSuccess
            assertEquals(1, partial.savedItems.size)
            assertEquals(1, partial.failedCount)
            assertTrue(partial.failureMessage.contains("1 file tersimpan"))
            assertTrue(partial.failureMessage.contains("1 gagal"))
            assertEquals(partial.failureMessage, vm.state.value.errorMessage)
            assertEquals(1, storage.savedFiles.size)
            assertEquals(1, history.itemsFlow.value.size)

            vm.effect.test {
                expectNoEvents()
            }

            vm.onEvent(StudioEditorUiEvent.DismissCompleted)
            assertEquals(StudioProcessingState.Idle, vm.state.value.processingState)
            assertNull(vm.state.value.errorMessage)
        }

    @Test
    fun `save to gallery emits SavedSuccessfully and empty load resets state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val uri = MediaUri("content://gallery/photo/saveonly")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(uri, MediaType.PHOTO, 3000, 4000, 2_000_000L))
                }
            val vm = createViewModel(metadataRepo = metadataRepo)

            vm.onEvent(StudioEditorUiEvent.LoadSources(listOf(uri)))
            advanceUntilIdle()

            vm.effect.test {
                vm.onEvent(StudioEditorUiEvent.SaveToGallery)
                advanceUntilIdle()

                val savedEffect = awaitItem() as StudioEditorUiEffect.SavedSuccessfully
                assertEquals(1, savedEffect.items.size)
            }

            vm.onEvent(StudioEditorUiEvent.LoadSources(emptyList()))
            assertTrue(vm.state.value.queue.isEmpty())
            assertNull(vm.state.value.activeSpec)
        }

    @Test
    fun `cancelling optimization halts render and resets processing state to Idle`() =
        runTest(mainDispatcherRule.dispatcher) {
            val uri = MediaUri("content://gallery/video/long")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putSuccess(MediaMetadata(uri, MediaType.VIDEO, 3840, 2160, 50_000_000L, durationMs = 45_000L))
                }
            val engine = FakeMediaOptimizerEngine().apply { renderDelayMs = 5_000L }
            val storage = FakeMediaStorageRepository()
            val vm = createViewModel(metadataRepo = metadataRepo, engine = engine, storage = storage)

            vm.onEvent(StudioEditorUiEvent.LoadSources(listOf(uri)))
            advanceUntilIdle()

            vm.onEvent(StudioEditorUiEvent.SaveToGallery)
            advanceTimeBy(1_000L)
            assertTrue(vm.state.value.isProcessing)

            vm.onEvent(StudioEditorUiEvent.CancelOptimization)
            advanceUntilIdle()

            assertFalse(vm.state.value.isProcessing)
            assertEquals(StudioProcessingState.Idle, vm.state.value.processingState)
            assertTrue(storage.savedFiles.isEmpty())
        }

    @Test
    fun `failed inspect and failed engine run expose dismissible error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val badUri = MediaUri("content://gallery/photo/missing")
            val okUri = MediaUri("content://gallery/photo/bad")
            val metadataRepo =
                FakeMediaMetadataRepository().apply {
                    putFailure(badUri, AppError.PermissionDenied("denied"))
                    putSuccess(MediaMetadata(okUri, MediaType.PHOTO, 1000, 1000, 500_000L))
                }
            val engine =
                FakeMediaOptimizerEngine().apply {
                    failWith = IllegalStateException("Decoder crashed")
                }
            val vm = createViewModel(metadataRepo = metadataRepo, engine = engine)

            vm.onEvent(StudioEditorUiEvent.LoadSources(listOf(badUri, okUri)))
            advanceUntilIdle()
            assertNotNull(vm.state.value.errorMessage)

            vm.onEvent(StudioEditorUiEvent.DismissError)
            assertNull(vm.state.value.errorMessage)

            vm.onEvent(StudioEditorUiEvent.SaveToGallery)
            advanceUntilIdle()

            assertEquals(StudioProcessingState.Idle, vm.state.value.processingState)
            assertNotNull(vm.state.value.errorMessage)
        }

    private fun createViewModel(
        metadataRepo: FakeMediaMetadataRepository = FakeMediaMetadataRepository(),
        engine: FakeMediaOptimizerEngine = FakeMediaOptimizerEngine(),
        storage: FakeMediaStorageRepository = FakeMediaStorageRepository(),
        history: FakeOptimizationHistoryRepository = FakeOptimizationHistoryRepository(),
        prefsRepo: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        handle: SavedStateHandle = SavedStateHandle(),
    ): StudioEditorViewModel {
        val calc = CalculateOptimalSpecUseCase()
        return StudioEditorViewModel(
            calculateOptimalSpec = calc,
            optimizeMedia = OptimizeMediaUseCase(calc, engine, storage, history),
            metadataRepository = metadataRepo,
            preferencesRepository = prefsRepo,
            savedStateHandle = handle,
        )
    }
}
