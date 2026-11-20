package com.zidee.ress.presentation.dashboard

import app.cash.turbine.test
import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.usecase.ObserveOptimizationHistoryUseCase
import com.zidee.ress.presentation.fakes.FakeMediaStorageRepository
import com.zidee.ress.presentation.fakes.FakeOptimizationHistoryRepository
import com.zidee.ress.presentation.fakes.FakeUserPreferencesRepository
import com.zidee.ress.presentation.fakes.MainDispatcherRule
import com.zidee.ress.presentation.share.DirectShareTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `observes album history and prunes items deleted from system gallery`() =
        runTest(mainDispatcherRule.dispatcher) {
            val aliveItem = historyItem("alive", "content://media/external/video/1", TargetPlatform.WA_STORY)
            val deletedItem = historyItem("gone", "content://media/external/video/missing", TargetPlatform.IG_STORY)
            val storage =
                FakeMediaStorageRepository().apply {
                    existingUris += aliveItem.mediaStoreUri.value
                }
            val history = FakeOptimizationHistoryRepository(listOf(aliveItem, deletedItem))
            val prefs = FakeUserPreferencesRepository(UserPreferences(lastSelectedPlatform = TargetPlatform.IG_STORY))

            val vm =
                DashboardViewModel(
                    observeOptimizationHistory = ObserveOptimizationHistoryUseCase(history, storage),
                    storageRepository = storage,
                    historyRepository = history,
                    preferencesRepository = prefs,
                )

            vm.state.test {
                assertTrue(awaitItem().isLoadingHistory)
                advanceUntilIdle()
                val loaded = expectMostRecentItem()
                assertFalse(loaded.isLoadingHistory)
                assertEquals(listOf(aliveItem), loaded.historyItems)
                assertEquals(TargetPlatform.IG_STORY, loaded.preferences.lastSelectedPlatform)
            }
            assertTrue(history.deletedIds.contains("gone"))
        }

    @Test
    fun `platform shortcut opens picker and media picked navigates to studio`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm =
                DashboardViewModel(
                    observeOptimizationHistory =
                        ObserveOptimizationHistoryUseCase(
                            FakeOptimizationHistoryRepository(),
                            FakeMediaStorageRepository(),
                        ),
                    storageRepository = FakeMediaStorageRepository(),
                    historyRepository = FakeOptimizationHistoryRepository(),
                    preferencesRepository =
                        FakeUserPreferencesRepository(
                            UserPreferences(lastSelectedPlatform = TargetPlatform.IG_POST),
                        ),
                )
            advanceUntilIdle()

            vm.effect.test {
                vm.onEvent(DashboardUiEvent.SelectPlatformShortcut(TargetPlatform.WA_CHAT))
                assertEquals(DashboardUiEffect.OpenMediaPicker(TargetPlatform.WA_CHAT), awaitItem())

                val picked = listOf(MediaUri("content://gallery/photo/1"), MediaUri("content://gallery/photo/2"))
                vm.onEvent(DashboardUiEvent.MediaPicked(picked))
                assertEquals(
                    DashboardUiEffect.NavigateToStudio(picked, TargetPlatform.IG_POST),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `1-click re-share launches direct share without re-rendering when file exists`() =
        runTest(mainDispatcherRule.dispatcher) {
            val savedItem = historyItem("1", "content://media/external/video/10", TargetPlatform.WA_STORY)
            val storage =
                FakeMediaStorageRepository().apply {
                    existingUris += savedItem.mediaStoreUri.value
                }
            val history = FakeOptimizationHistoryRepository(listOf(savedItem))
            val vm =
                DashboardViewModel(
                    observeOptimizationHistory = ObserveOptimizationHistoryUseCase(history, storage),
                    storageRepository = storage,
                    historyRepository = history,
                    preferencesRepository = FakeUserPreferencesRepository(),
                )
            advanceUntilIdle()

            vm.effect.test {
                vm.onEvent(DashboardUiEvent.ReShareItem(savedItem, DirectShareTarget.INSTAGRAM))
                advanceUntilIdle()

                val eff = awaitItem() as DashboardUiEffect.LaunchShare
                assertEquals(listOf(savedItem.mediaStoreUri), eff.payload.uris)
                assertEquals("com.instagram.android", eff.payload.targetPackage)
            }
        }

    @Test
    fun `batch re-share filters missing files and launches share for remaining items`() =
        runTest(mainDispatcherRule.dispatcher) {
            val first = historyItem("1", "content://media/external/video/1", TargetPlatform.WA_STORY)
            val second = historyItem("2", "content://media/external/video/2", TargetPlatform.WA_STORY)
            val storage =
                FakeMediaStorageRepository().apply {
                    existingUris += first.mediaStoreUri.value
                }
            val history = FakeOptimizationHistoryRepository(listOf(first, second))
            val vm =
                DashboardViewModel(
                    observeOptimizationHistory = ObserveOptimizationHistoryUseCase(history, storage),
                    storageRepository = storage,
                    historyRepository = history,
                    preferencesRepository = FakeUserPreferencesRepository(),
                )
            advanceUntilIdle()

            vm.effect.test {
                vm.onEvent(DashboardUiEvent.ReShareItems(listOf(first, second)))
                advanceUntilIdle()

                val eff = awaitItem() as DashboardUiEffect.LaunchShare
                assertEquals(listOf(first.mediaStoreUri), eff.payload.uris)
            }
        }

    @Test
    fun `re-share deletes stale DB row and emits message if user removed file externally`() =
        runTest(mainDispatcherRule.dispatcher) {
            val goneItem = historyItem("stale-1", "content://media/external/images/deleted", TargetPlatform.IG_POST)
            val storage = FakeMediaStorageRepository()
            val history = FakeOptimizationHistoryRepository(listOf(goneItem))
            val vm =
                DashboardViewModel(
                    observeOptimizationHistory = ObserveOptimizationHistoryUseCase(history, storage),
                    storageRepository = storage,
                    historyRepository = history,
                    preferencesRepository = FakeUserPreferencesRepository(),
                )
            advanceUntilIdle()

            vm.effect.test {
                vm.onEvent(DashboardUiEvent.ReShareItem(goneItem))
                advanceUntilIdle()

                assertTrue(awaitItem() is DashboardUiEffect.ShowMessage)
            }
            assertTrue(history.deletedIds.contains("stale-1"))
            assertNotNull(vm.state.value.errorMessage)

            vm.onEvent(DashboardUiEvent.DismissError)
            assertNull(vm.state.value.errorMessage)
        }

    @Test
    fun `delete history item removes entry and surfaces error on failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val item = historyItem("del-1", "content://media/external/video/5", TargetPlatform.WA_CHAT)
            val storage = FakeMediaStorageRepository().apply { existingUris += item.mediaStoreUri.value }
            val history = FakeOptimizationHistoryRepository(listOf(item))
            val vm =
                DashboardViewModel(
                    observeOptimizationHistory = ObserveOptimizationHistoryUseCase(history, storage),
                    storageRepository = storage,
                    historyRepository = history,
                    preferencesRepository = FakeUserPreferencesRepository(),
                )
            advanceUntilIdle()

            vm.onEvent(DashboardUiEvent.DeleteHistoryItem(item.id))
            advanceUntilIdle()
            assertTrue(history.deletedIds.contains(item.id))

            history.failDelete = AppError.StorageWrite("disk locked")
            vm.onEvent(DashboardUiEvent.DeleteHistoryItem("another"))
            advanceUntilIdle()
            assertNotNull(vm.state.value.errorMessage)
        }

    @Test
    fun `history stream failure stops loading and sets Indonesian error message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failingHistory =
                object : OptimizationHistoryRepository {
                    override fun observeHistory(): Flow<List<OptimizedMediaItem>> = flow { error("DB corrupt") }

                    override suspend fun record(items: List<OptimizedMediaItem>) = AppResult.Success(Unit)

                    override suspend fun delete(id: String) = AppResult.Success(Unit)
                }
            val storage = FakeMediaStorageRepository()
            val vm =
                DashboardViewModel(
                    observeOptimizationHistory = ObserveOptimizationHistoryUseCase(failingHistory, storage),
                    storageRepository = storage,
                    historyRepository = failingHistory,
                    preferencesRepository = FakeUserPreferencesRepository(),
                )
            advanceUntilIdle()

            assertFalse(vm.state.value.isLoadingHistory)
            assertNotNull(vm.state.value.errorMessage)
        }

    private fun historyItem(
        id: String,
        uri: String,
        platform: TargetPlatform,
    ) = OptimizedMediaItem(
        id = id,
        mediaStoreUri = MediaUri(uri),
        displayName = "ZIDEE_$id.mp4",
        mediaType = MediaType.VIDEO,
        platform = platform,
        aspectMode = CanvasAspectMode.BLUR_BG_9_16,
        originalSize = PixelSize(1920, 1080),
        outputSize = PixelSize(1080, 1920),
        originalSizeBytes = 12_000_000L,
        outputSizeBytes = 3_200_000L,
        durationMs = 15_000L,
        partIndex = 1,
        totalParts = 1,
        createdAtEpochMs = 100L,
    )
}
