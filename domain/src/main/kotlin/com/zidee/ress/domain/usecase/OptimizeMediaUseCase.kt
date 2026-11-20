package com.zidee.ress.domain.usecase

import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.repository.MediaOptimizerEngine
import com.zidee.ress.domain.repository.MediaStorageRepository
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed interface OptimizationStatus {
    data class Rendering(val progressFraction: Float) : OptimizationStatus

    data class SavingToGallery(val currentPart: Int, val totalParts: Int) : OptimizationStatus

    data class Completed(val items: List<OptimizedMediaItem>) : OptimizationStatus

    data class Failed(val error: AppError) : OptimizationStatus
}

/**
 * Runs one full offline optimization job:
 * calculate spec -> render to staging -> save permanently to `ZideeResolution` -> record history.
 */
class OptimizeMediaUseCase(
    private val calculateOptimalSpec: CalculateOptimalSpecUseCase,
    private val engine: MediaOptimizerEngine,
    private val storage: MediaStorageRepository,
    private val history: OptimizationHistoryRepository,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    operator fun invoke(request: OptimizationRequest): Flow<OptimizationStatus> =
        flow {
            val spec = calculateOptimalSpec(request)
            val timestampMs = nowEpochMs()
            val stamp = TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(timestampMs).atZone(zoneId))

            var outputs: List<StagedOutput>? = null
            try {
                engine.render(request.source.uri, spec).collect { progress ->
                    when (progress) {
                        is EngineProgress.Running -> emit(OptimizationStatus.Rendering(progress.fraction))
                        is EngineProgress.Completed -> outputs = progress.outputs
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                emit(OptimizationStatus.Failed(AppError.Processing(t.message ?: "Media processing failed", t)))
                return@flow
            }

            val rendered = outputs
            if (rendered.isNullOrEmpty()) {
                emit(OptimizationStatus.Failed(AppError.Processing("Media engine finished with no output")))
                return@flow
            }

            val pendingDiscard = rendered.toMutableList()
            val savedItems = mutableListOf<OptimizedMediaItem>()
            try {
                for (staged in rendered) {
                    emit(OptimizationStatus.SavingToGallery(staged.partIndex, staged.totalParts))
                    val fileName =
                        buildOutputFileName(
                            mediaType = staged.mediaType,
                            platform = request.platform,
                            timestamp = stamp,
                            partIndex = staged.partIndex,
                            totalParts = staged.totalParts,
                        )
                    when (val saved = storage.saveToGallery(staged, fileName)) {
                        is AppResult.Failure -> {
                            pendingDiscard.remove(staged)
                            emit(OptimizationStatus.Failed(saved.error))
                            return@flow
                        }
                        is AppResult.Success -> {
                            pendingDiscard.remove(staged)
                            savedItems +=
                                OptimizedMediaItem(
                                    id = idGenerator(),
                                    mediaStoreUri = saved.value,
                                    displayName = fileName,
                                    mediaType = staged.mediaType,
                                    platform = request.platform,
                                    aspectMode = request.aspectMode,
                                    originalSize = spec.sourceSize,
                                    outputSize = staged.size,
                                    originalSizeBytes = request.source.sizeBytes,
                                    outputSizeBytes = staged.sizeBytes,
                                    durationMs = staged.durationMs,
                                    partIndex = staged.partIndex,
                                    totalParts = staged.totalParts,
                                    createdAtEpochMs = nowEpochMs(),
                                )
                        }
                    }
                }
            } finally {
                if (pendingDiscard.isNotEmpty()) {
                    withContext(NonCancellable) {
                        pendingDiscard.forEach { storage.discardStaged(it) }
                    }
                }
            }

            when (val recorded = history.record(savedItems)) {
                is AppResult.Failure -> emit(OptimizationStatus.Failed(recorded.error))
                is AppResult.Success -> emit(OptimizationStatus.Completed(savedItems))
            }
        }

    internal companion object {
        private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        fun buildOutputFileName(
            mediaType: MediaType,
            platform: TargetPlatform,
            timestamp: String,
            partIndex: Int,
            totalParts: Int,
        ): String {
            val prefix =
                when (mediaType) {
                    MediaType.PHOTO -> "ZIDEE_IMG"
                    MediaType.VIDEO -> "ZIDEE_VID"
                }
            val ext =
                when (mediaType) {
                    MediaType.PHOTO -> "jpg"
                    MediaType.VIDEO -> "mp4"
                }
            val partSuffix =
                if (totalParts > 1) {
                    "_part${partIndex.toString().padStart(2, '0')}"
                } else {
                    ""
                }
            return "${prefix}_${timestamp}_${platform.name}$partSuffix.$ext"
        }
    }
}
