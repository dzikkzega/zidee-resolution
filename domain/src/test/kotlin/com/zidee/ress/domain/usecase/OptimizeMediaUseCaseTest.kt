package com.zidee.ress.domain.usecase

import app.cash.turbine.test
import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.repository.MediaOptimizerEngine
import com.zidee.ress.domain.repository.MediaStorageRepository
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class OptimizeMediaUseCaseTest {
    @Test
    fun `renders, saves every part to gallery, and records history`() =
        runTest {
            val engine =
                FakeMediaOptimizerEngine(
                    listOf(
                        EngineProgress.Running(0.4f),
                        EngineProgress.Completed(
                            listOf(
                                StagedOutput("/cache/p1.mp4", MediaType.VIDEO, PixelSize(1080, 1920), 1_000, 30_000, 1, 2),
                                StagedOutput("/cache/p2.mp4", MediaType.VIDEO, PixelSize(1080, 1920), 500, 15_000, 2, 2),
                            ),
                        ),
                    ),
                )
            val storage = FakeMediaStorageRepository()
            val history = FakeOptimizationHistoryRepository()
            var nextId = 1
            val useCase =
                OptimizeMediaUseCase(
                    calculateOptimalSpec = CalculateOptimalSpecUseCase(),
                    engine = engine,
                    storage = storage,
                    history = history,
                    nowEpochMs = { 1_700_000_000_000L },
                    idGenerator = { "id-${nextId++}" },
                    zoneId = ZoneOffset.UTC,
                )

            val request =
                OptimizationRequest(
                    source =
                        MediaMetadata(
                            uri = MediaUri("content://gallery/v/1"),
                            mediaType = MediaType.VIDEO,
                            width = 1080,
                            height = 1920,
                            sizeBytes = 5_000_000,
                            durationMs = 45_000,
                        ),
                    platform = TargetPlatform.WA_STORY,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                )

            useCase(request).test {
                assertEquals(OptimizationStatus.Rendering(0.4f), awaitItem())
                assertEquals(OptimizationStatus.SavingToGallery(1, 2), awaitItem())
                assertEquals(OptimizationStatus.SavingToGallery(2, 2), awaitItem())

                val done = awaitItem() as OptimizationStatus.Completed
                assertEquals(2, done.items.size)
                assertTrue(done.items[0].displayName.endsWith("_WA_STORY_part01.mp4"))
                assertTrue(done.items[1].displayName.endsWith("_WA_STORY_part02.mp4"))
                awaitComplete()
            }

            assertEquals(2, storage.savedNames.size)
            assertTrue(storage.discardedPaths.isEmpty())
            assertEquals(2, history.items.value.size)
        }

    @Test
    fun `discards remaining staged parts when saving an earlier part fails`() =
        runTest {
            val engine =
                FakeMediaOptimizerEngine(
                    listOf(
                        EngineProgress.Completed(
                            listOf(
                                StagedOutput("/cache/p1.mp4", MediaType.VIDEO, PixelSize(1080, 1920), 1_000, 30_000, 1, 3),
                                StagedOutput("/cache/p2.mp4", MediaType.VIDEO, PixelSize(1080, 1920), 1_000, 30_000, 2, 3),
                                StagedOutput("/cache/p3.mp4", MediaType.VIDEO, PixelSize(1080, 1920), 500, 15_000, 3, 3),
                            ),
                        ),
                    ),
                )
            val storage = FakeMediaStorageRepository(failOnPartIndex = 2)
            val history = FakeOptimizationHistoryRepository()
            val useCase = OptimizeMediaUseCase(CalculateOptimalSpecUseCase(), engine, storage, history)

            val request =
                OptimizationRequest(
                    source = MediaMetadata(MediaUri("content://v/1"), MediaType.VIDEO, 1080, 1920, 5_000_000, durationMs = 75_000),
                    platform = TargetPlatform.WA_STORY,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                )

            useCase(request).test {
                assertEquals(OptimizationStatus.SavingToGallery(1, 3), awaitItem())
                assertEquals(OptimizationStatus.SavingToGallery(2, 3), awaitItem())
                val failed = awaitItem() as OptimizationStatus.Failed
                assertTrue(failed.error is AppError.StorageWrite)
                awaitComplete()
            }

            assertEquals(listOf("/cache/p3.mp4"), storage.discardedPaths)
        }

    @Test
    fun `maps engine exception to Processing failure without saving to gallery`() =
        runTest {
            val failingEngine =
                object : MediaOptimizerEngine {
                    override fun render(
                        source: MediaUri,
                        spec: OptimalMediaSpec,
                    ): Flow<EngineProgress> =
                        flow {
                            emit(EngineProgress.Running(0.1f))
                            error("decode error")
                        }
                }
            val storage = FakeMediaStorageRepository()
            val history = FakeOptimizationHistoryRepository()
            val useCase = OptimizeMediaUseCase(CalculateOptimalSpecUseCase(), failingEngine, storage, history)

            val request =
                OptimizationRequest(
                    source = MediaMetadata(MediaUri("content://p/1"), MediaType.PHOTO, 4000, 3000, 1_000_000),
                    platform = TargetPlatform.IG_STORY,
                    aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                )

            useCase(request).test {
                assertEquals(OptimizationStatus.Rendering(0.1f), awaitItem())
                val failed = awaitItem() as OptimizationStatus.Failed
                assertTrue(failed.error is AppError.Processing)
                awaitComplete()
            }

            assertTrue(storage.savedNames.isEmpty())
            assertTrue(history.items.value.isEmpty())
        }

    @Test
    fun `observe history filters out entries deleted externally and prunes them`() =
        runTest {
            val storage = FakeMediaStorageRepository(existingUris = mutableSetOf("content://media/keep"))
            val history =
                FakeOptimizationHistoryRepository(
                    initial =
                        listOf(
                            historyItem("keep", "content://media/keep"),
                            historyItem("gone", "content://media/gone"),
                        ),
                )
            val observe = ObserveOptimizationHistoryUseCase(history, storage)

            observe().test {
                val visible = awaitItem()
                assertEquals(listOf("keep"), visible.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("keep"), history.items.value.map { it.id })
        }

    private fun historyItem(
        id: String,
        uri: String,
    ) = OptimizedMediaItem(
        id = id,
        mediaStoreUri = MediaUri(uri),
        displayName = "ZIDEE_IMG.jpg",
        mediaType = MediaType.PHOTO,
        platform = TargetPlatform.WA_CHAT,
        aspectMode = CanvasAspectMode.ORIGINAL,
        originalSize = PixelSize(4000, 3000),
        outputSize = PixelSize(2048, 1536),
        originalSizeBytes = 2_000_000,
        outputSizeBytes = 500_000,
        durationMs = null,
        partIndex = 1,
        totalParts = 1,
        createdAtEpochMs = 100L,
    )

    private class FakeMediaOptimizerEngine(
        private val emissions: List<EngineProgress>,
    ) : MediaOptimizerEngine {
        override fun render(
            source: MediaUri,
            spec: OptimalMediaSpec,
        ): Flow<EngineProgress> =
            flow {
                emissions.forEach { emit(it) }
            }
    }

    private class FakeMediaStorageRepository(
        private val existingUris: MutableSet<String> = mutableSetOf(),
        private val failOnPartIndex: Int? = null,
    ) : MediaStorageRepository {
        val savedNames = mutableListOf<String>()
        val discardedPaths = mutableListOf<String>()

        override suspend fun saveToGallery(
            staged: StagedOutput,
            displayName: String,
        ): AppResult<MediaUri> {
            if (staged.partIndex == failOnPartIndex) {
                return AppResult.Failure(AppError.StorageWrite("disk full"))
            }
            savedNames += displayName
            val uri = "content://media/external/$displayName"
            existingUris += uri
            return AppResult.Success(MediaUri(uri))
        }

        override suspend fun discardStaged(staged: StagedOutput) {
            discardedPaths += staged.cachePath
        }

        override suspend fun exists(uri: MediaUri): Boolean = uri.value in existingUris
    }

    private class FakeOptimizationHistoryRepository(
        initial: List<OptimizedMediaItem> = emptyList(),
    ) : OptimizationHistoryRepository {
        val items = MutableStateFlow(initial)

        override fun observeHistory(): Flow<List<OptimizedMediaItem>> = items

        override suspend fun record(items: List<OptimizedMediaItem>): AppResult<Unit> {
            this.items.value = items + this.items.value
            return AppResult.Success(Unit)
        }

        override suspend fun delete(id: String): AppResult<Unit> {
            items.value = items.value.filterNot { it.id == id }
            return AppResult.Success(Unit)
        }
    }
}
