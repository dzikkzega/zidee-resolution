package com.zidee.ress.data.repository

import app.cash.turbine.test
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.data.local.dao.OptimizationHistoryDao
import com.zidee.ress.data.local.entity.OptimizationHistoryEntity
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRepositoryImplTest {
    @Test
    fun `records, maps, observes newest-first, and deletes history items`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val dao = FakeOptimizationHistoryDao()
            val repo = OptimizationHistoryRepositoryImpl(dao, TestDispatchers(dispatcher))

            val older = sampleItem("id-1", createdAt = 1_000L, platform = TargetPlatform.WA_CHAT)
            val newer = sampleItem("id-2", createdAt = 2_000L, platform = TargetPlatform.IG_STORY)

            assertTrue(repo.record(listOf(older, newer)) is AppResult.Success)

            repo.observeHistory().test {
                val loaded = awaitItem()
                assertEquals(listOf("id-2", "id-1"), loaded.map { it.id })
                assertEquals(TargetPlatform.IG_STORY, loaded[0].platform)
                assertEquals(PixelSize(1080, 1920), loaded[0].outputSize)

                assertTrue(repo.delete("id-2") is AppResult.Success)
                val afterDelete = awaitItem()
                assertEquals(listOf("id-1"), afterDelete.map { it.id })
            }
        }

    private fun sampleItem(
        id: String,
        createdAt: Long,
        platform: TargetPlatform,
    ) = OptimizedMediaItem(
        id = id,
        mediaStoreUri = MediaUri("content://media/external/video/$id"),
        displayName = "ZIDEE_VID_$id.mp4",
        mediaType = MediaType.VIDEO,
        platform = platform,
        aspectMode = CanvasAspectMode.BLUR_BG_9_16,
        originalSize = PixelSize(1920, 1080),
        outputSize = PixelSize(1080, 1920),
        originalSizeBytes = 20_000_000L,
        outputSizeBytes = 4_000_000L,
        durationMs = 30_000L,
        partIndex = 1,
        totalParts = 2,
        createdAtEpochMs = createdAt,
    )

    private class FakeOptimizationHistoryDao : OptimizationHistoryDao {
        private val rows = MutableStateFlow<List<OptimizationHistoryEntity>>(emptyList())

        override fun observeAllDesc(): Flow<List<OptimizationHistoryEntity>> =
            rows.map { list -> list.sortedByDescending { it.createdAtEpochMs } }

        override suspend fun insertAll(entities: List<OptimizationHistoryEntity>) {
            val byId = rows.value.associateBy { it.id }.toMutableMap()
            entities.forEach { byId[it.id] = it }
            rows.value = byId.values.toList()
        }

        override suspend fun deleteById(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun clearAll() {
            rows.value = emptyList()
        }
    }

    private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher = d
        override val io: CoroutineDispatcher = d
        override val default: CoroutineDispatcher = d
    }
}
