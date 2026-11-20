package com.zidee.ress.data.repository

import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.data.local.dao.OptimizationHistoryDao
import com.zidee.ress.data.local.entity.OptimizationHistoryEntity
import com.zidee.ress.data.mediastore.ZideeMediaStoreSaver
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.MediaStorageRepository
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OptimizationHistoryRepositoryImpl(
    private val dao: OptimizationHistoryDao,
    private val dispatchers: DispatcherProvider,
) : OptimizationHistoryRepository {
    override fun observeHistory(): Flow<List<OptimizedMediaItem>> =
        dao.observeAllDesc()
            .map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
            .flowOn(dispatchers.io)

    override suspend fun record(items: List<OptimizedMediaItem>): AppResult<Unit> =
        withContext(dispatchers.io) {
            if (items.isEmpty()) return@withContext AppResult.Success(Unit)
            try {
                dao.insertAll(items.map { it.toEntity() })
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AppResult.Failure(AppError.StorageWrite(t.message ?: "Failed saving optimization history", t))
            }
        }

    override suspend fun delete(id: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                dao.deleteById(id)
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AppResult.Failure(AppError.StorageWrite(t.message ?: "Failed deleting history item", t))
            }
        }
}

class MediaRepositoryImpl(
    private val storageSaver: ZideeMediaStoreSaver,
    private val historyRepository: OptimizationHistoryRepositoryImpl,
) : MediaStorageRepository by storageSaver,
    OptimizationHistoryRepository by historyRepository

internal fun OptimizationHistoryEntity.toDomainOrNull(): OptimizedMediaItem? =
    try {
        OptimizedMediaItem(
            id = id,
            mediaStoreUri = MediaUri(mediaStoreUri),
            displayName = displayName,
            mediaType = MediaType.valueOf(mediaType),
            platform = TargetPlatform.valueOf(targetPlatform),
            aspectMode = CanvasAspectMode.valueOf(aspectMode),
            originalSize = PixelSize(originalWidth, originalHeight),
            outputSize = PixelSize(outputWidth, outputHeight),
            originalSizeBytes = originalSizeBytes,
            outputSizeBytes = outputSizeBytes,
            durationMs = durationMs,
            partIndex = partIndex,
            totalParts = totalParts,
            createdAtEpochMs = createdAtEpochMs,
        )
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun OptimizedMediaItem.toEntity(): OptimizationHistoryEntity =
    OptimizationHistoryEntity(
        id = id,
        mediaStoreUri = mediaStoreUri.value,
        displayName = displayName,
        mediaType = mediaType.name,
        targetPlatform = platform.name,
        aspectMode = aspectMode.name,
        originalWidth = originalSize.width,
        originalHeight = originalSize.height,
        outputWidth = outputSize.width,
        outputHeight = outputSize.height,
        originalSizeBytes = originalSizeBytes,
        outputSizeBytes = outputSizeBytes,
        durationMs = durationMs,
        partIndex = partIndex,
        totalParts = totalParts,
        createdAtEpochMs = createdAtEpochMs,
    )
