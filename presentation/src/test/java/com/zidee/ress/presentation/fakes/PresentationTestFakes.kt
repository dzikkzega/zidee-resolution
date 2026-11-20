package com.zidee.ress.presentation.fakes

import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.repository.MediaMetadataRepository
import com.zidee.ress.domain.repository.MediaOptimizerEngine
import com.zidee.ress.domain.repository.MediaStorageRepository
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FakeMediaMetadataRepository : MediaMetadataRepository {
    private val entries = mutableMapOf<String, AppResult<MediaMetadata>>()

    fun putSuccess(metadata: MediaMetadata) {
        entries[metadata.uri.value] = AppResult.Success(metadata)
    }

    fun putFailure(
        uri: MediaUri,
        error: AppError,
    ) {
        entries[uri.value] = AppResult.Failure(error)
    }

    override suspend fun inspect(uri: MediaUri): AppResult<MediaMetadata> =
        entries[uri.value] ?: AppResult.Failure(AppError.PermissionDenied("Unknown test URI: ${uri.value}"))
}

class FakeMediaOptimizerEngine : MediaOptimizerEngine {
    var renderDelayMs: Long = 0L
    var failWith: Throwable? = null
    val failForUris: MutableMap<String, Throwable> = mutableMapOf()
    var renderCount: Int = 0
    var lastSpec: OptimalMediaSpec? = null

    override fun render(
        source: MediaUri,
        spec: OptimalMediaSpec,
    ): Flow<EngineProgress> =
        flow {
            renderCount++
            lastSpec = spec
            emit(EngineProgress.Running(0.25f))
            if (renderDelayMs > 0L) delay(renderDelayMs)
            failForUris[source.value]?.let { throw it }
            failWith?.let { throw it }
            emit(EngineProgress.Running(0.85f))

            val outputs =
                if (spec.mediaType == MediaType.VIDEO && spec.video?.segmentDurationsMs?.size?.let { it > 1 } == true) {
                    val parts = spec.video?.segmentDurationsMs.orEmpty()
                    parts.mapIndexed { idx, dur ->
                        StagedOutput(
                            cachePath = "/cache/part_${idx + 1}.mp4",
                            mediaType = MediaType.VIDEO,
                            size = spec.outputSize,
                            sizeBytes = 256_000L,
                            durationMs = dur,
                            partIndex = idx + 1,
                            totalParts = parts.size,
                        )
                    }
                } else {
                    listOf(
                        StagedOutput(
                            cachePath = if (spec.mediaType == MediaType.VIDEO) "/cache/out.mp4" else "/cache/out.jpg",
                            mediaType = spec.mediaType,
                            size = spec.outputSize,
                            sizeBytes = 128_000L,
                            durationMs = if (spec.mediaType == MediaType.VIDEO) 10_000L else null,
                            partIndex = 1,
                            totalParts = 1,
                        ),
                    )
                }
            emit(EngineProgress.Completed(outputs))
        }
}

class FakeMediaStorageRepository : MediaStorageRepository {
    val savedFiles = mutableListOf<Pair<String, StagedOutput>>()
    val discarded = mutableListOf<StagedOutput>()
    val existingUris = mutableSetOf<String>()
    var failSave: AppError? = null
    private var nextId = 1

    override suspend fun saveToGallery(
        staged: StagedOutput,
        displayName: String,
    ): AppResult<MediaUri> {
        failSave?.let { return AppResult.Failure(it) }
        val subfolder = if (staged.mediaType == MediaType.PHOTO) "images" else "video"
        val uri = "content://media/external/$subfolder/${nextId++}"
        savedFiles += displayName to staged
        existingUris += uri
        return AppResult.Success(MediaUri(uri))
    }

    override suspend fun discardStaged(staged: StagedOutput) {
        discarded += staged
    }

    override suspend fun exists(uri: MediaUri): Boolean = uri.value in existingUris
}

class FakeOptimizationHistoryRepository(
    initial: List<OptimizedMediaItem> = emptyList(),
) : OptimizationHistoryRepository {
    val itemsFlow = MutableStateFlow(initial)
    val deletedIds = mutableListOf<String>()
    var failDelete: AppError? = null

    override fun observeHistory(): Flow<List<OptimizedMediaItem>> = itemsFlow

    override suspend fun record(items: List<OptimizedMediaItem>): AppResult<Unit> {
        itemsFlow.value = items + itemsFlow.value
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        failDelete?.let { return AppResult.Failure(it) }
        deletedIds += id
        itemsFlow.value = itemsFlow.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }
}

class FakeUserPreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesRepository {
    val prefsFlow = MutableStateFlow(initial)
    var lastSelectionSaved: Pair<TargetPlatform, CanvasAspectMode>? = null
    var lastEncoderSaved: VideoEncoder? = null

    override fun observePreferences(): Flow<UserPreferences> = prefsFlow

    override suspend fun setLastSelection(
        platform: TargetPlatform,
        aspectMode: CanvasAspectMode,
    ): AppResult<Unit> {
        lastSelectionSaved = platform to aspectMode
        prefsFlow.value =
            prefsFlow.value.copy(
                lastSelectedPlatform = platform,
                lastSelectedAspectMode = aspectMode,
            )
        return AppResult.Success(Unit)
    }

    override suspend fun setPreferredEncoder(encoder: VideoEncoder): AppResult<Unit> {
        lastEncoderSaved = encoder
        prefsFlow.value = prefsFlow.value.copy(preferredVideoEncoder = encoder)
        return AppResult.Success(Unit)
    }

    override suspend fun setDefaultSharpnessPercent(percent: Int?): AppResult<Unit> {
        prefsFlow.value = prefsFlow.value.copy(defaultSharpnessPercent = percent)
        return AppResult.Success(Unit)
    }
}
