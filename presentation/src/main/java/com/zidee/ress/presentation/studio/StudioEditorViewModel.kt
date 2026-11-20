package com.zidee.ress.presentation.studio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.CropAdjustment
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PhotoAdvancedConfig
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.model.VideoAdvancedConfig
import com.zidee.ress.domain.repository.MediaMetadataRepository
import com.zidee.ress.domain.repository.UserPreferencesRepository
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.domain.usecase.OptimizationStatus
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import com.zidee.ress.presentation.common.toUserMessage
import com.zidee.ress.presentation.share.DirectShareTarget
import com.zidee.ress.presentation.share.SharePayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioQueueEntry(
    val uri: MediaUri,
    val metadata: MediaMetadata? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface StudioProcessingState {
    data object Idle : StudioProcessingState

    data class Running(
        val progressFraction: Float,
        val currentItemIndex: Int,
        val totalItems: Int,
        val savingPart: Int? = null,
        val totalParts: Int? = null,
    ) : StudioProcessingState

    data class Completed(val items: List<OptimizedMediaItem>) : StudioProcessingState

    data class PartialSuccess(
        val savedItems: List<OptimizedMediaItem>,
        val failedCount: Int,
        val failureMessage: String,
    ) : StudioProcessingState
}

data class StudioEditorUiState(
    val queue: List<StudioQueueEntry> = emptyList(),
    val activeIndex: Int = 0,
    val selectedPlatform: TargetPlatform = TargetPlatform.WA_STORY,
    val selectedAspectMode: CanvasAspectMode = CanvasAspectMode.BLUR_BG_9_16,
    val cropAdjustment: CropAdjustment = CropAdjustment.CENTER,
    val blurRadiusPx: Int = CanvasGeometry.DEFAULT_BLUR_RADIUS_PX,
    val photoConfig: PhotoAdvancedConfig = PhotoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY),
    val videoConfig: VideoAdvancedConfig = VideoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY),
    val activeSpec: OptimalMediaSpec? = null,
    val splitFraction: Float = 0.5f,
    val isAdvancedExpanded: Boolean = false,
    val processingState: StudioProcessingState = StudioProcessingState.Idle,
    val errorMessage: String? = null,
) {
    val activeEntry: StudioQueueEntry? get() = queue.getOrNull(activeIndex)
    val isProcessing: Boolean get() = processingState is StudioProcessingState.Running
    val canExport: Boolean
        get() =
            queue.isNotEmpty() &&
                queue.any { it.metadata != null } &&
                processingState !is StudioProcessingState.Running
}

sealed interface StudioEditorUiEvent {
    data class LoadSources(
        val uris: List<MediaUri>,
        val initialPlatform: TargetPlatform? = null,
    ) : StudioEditorUiEvent

    data class SelectQueueIndex(val index: Int) : StudioEditorUiEvent

    data class SelectPlatform(val platform: TargetPlatform) : StudioEditorUiEvent

    data class SelectAspectMode(val mode: CanvasAspectMode) : StudioEditorUiEvent

    data class UpdateCrop(val adjustment: CropAdjustment) : StudioEditorUiEvent

    data class UpdateBlurRadius(val radiusPx: Int) : StudioEditorUiEvent

    data class UpdateSplitFraction(val fraction: Float) : StudioEditorUiEvent

    data object ToggleAdvancedPanel : StudioEditorUiEvent

    data class UpdatePhotoConfig(val transform: (PhotoAdvancedConfig) -> PhotoAdvancedConfig) : StudioEditorUiEvent

    data class UpdateVideoConfig(val transform: (VideoAdvancedConfig) -> VideoAdvancedConfig) : StudioEditorUiEvent

    data object SaveToGallery : StudioEditorUiEvent

    data class SaveAndShare(val target: DirectShareTarget? = null) : StudioEditorUiEvent

    data object CancelOptimization : StudioEditorUiEvent

    data object DismissError : StudioEditorUiEvent

    data object DismissCompleted : StudioEditorUiEvent
}

sealed interface StudioEditorUiEffect {
    data class LaunchShare(val payload: SharePayload) : StudioEditorUiEffect

    data class SavedSuccessfully(val items: List<OptimizedMediaItem>) : StudioEditorUiEffect
}

/**
 * Interactive Studio Editor state holder supporting single or batch media queues,
 * real-time `Before/After Split-Slider`, Blur BG radius tuning, Crop 9:16 pan/zoom,
 * and `Advanced Pro` controls.
 */
class StudioEditorViewModel(
    private val calculateOptimalSpec: CalculateOptimalSpecUseCase,
    private val optimizeMedia: OptimizeMediaUseCase,
    private val metadataRepository: MediaMetadataRepository,
    private val preferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(StudioEditorUiState())
    val state: StateFlow<StudioEditorUiState> = _state.asStateFlow()

    private val _effect = Channel<StudioEditorUiEffect>(Channel.BUFFERED)
    val effect: Flow<StudioEditorUiEffect> = _effect.receiveAsFlow()

    private var cachedPreferences: UserPreferences = UserPreferences()
    private var inspectJob: Job? = null
    private var optimizationJob: Job? = null

    init {
        val initialUris = extractInitialUris(savedStateHandle)
        val initialPlatform =
            savedStateHandle.get<String>(KEY_INITIAL_PLATFORM)
                ?.let { raw -> TargetPlatform.entries.firstOrNull { it.name == raw } }

        if (initialUris.isNotEmpty()) {
            loadSources(initialUris, initialPlatform)
        } else {
            hydrateInitialPreferences(initialPlatform)
        }
    }

    fun onEvent(event: StudioEditorUiEvent) {
        when (event) {
            is StudioEditorUiEvent.LoadSources -> loadSources(event.uris, event.initialPlatform)
            is StudioEditorUiEvent.SelectQueueIndex -> selectQueueIndex(event.index)
            is StudioEditorUiEvent.SelectPlatform -> selectPlatform(event.platform)
            is StudioEditorUiEvent.SelectAspectMode ->
                updateAndRecalculate {
                    it.copy(selectedAspectMode = event.mode)
                }
            is StudioEditorUiEvent.UpdateCrop ->
                updateAndRecalculate {
                    it.copy(cropAdjustment = event.adjustment)
                }
            is StudioEditorUiEvent.UpdateBlurRadius ->
                updateAndRecalculate {
                    it.copy(blurRadiusPx = event.radiusPx.coerceIn(CanvasGeometry.BLUR_RADIUS_RANGE))
                }
            is StudioEditorUiEvent.UpdateSplitFraction ->
                _state.update {
                    it.copy(splitFraction = event.fraction.coerceIn(0f, 1f))
                }
            StudioEditorUiEvent.ToggleAdvancedPanel ->
                _state.update {
                    it.copy(isAdvancedExpanded = !it.isAdvancedExpanded)
                }
            is StudioEditorUiEvent.UpdatePhotoConfig ->
                updateAndRecalculate {
                    it.copy(photoConfig = event.transform(it.photoConfig))
                }
            is StudioEditorUiEvent.UpdateVideoConfig ->
                updateAndRecalculate {
                    it.copy(videoConfig = event.transform(it.videoConfig))
                }
            StudioEditorUiEvent.SaveToGallery -> runOptimization(shareAfterSave = false, shareTarget = null)
            is StudioEditorUiEvent.SaveAndShare -> runOptimization(shareAfterSave = true, shareTarget = event.target)
            StudioEditorUiEvent.CancelOptimization -> cancelOptimization()
            StudioEditorUiEvent.DismissError -> _state.update { it.copy(errorMessage = null) }
            StudioEditorUiEvent.DismissCompleted ->
                _state.update {
                    if (it.processingState is StudioProcessingState.Completed ||
                        it.processingState is StudioProcessingState.PartialSuccess
                    ) {
                        it.copy(processingState = StudioProcessingState.Idle, errorMessage = null)
                    } else {
                        it
                    }
                }
        }
    }

    private fun hydrateInitialPreferences(overridePlatform: TargetPlatform?) {
        viewModelScope.launch {
            val prefs = readPreferencesSafely()
            cachedPreferences = prefs
            val platform = overridePlatform ?: prefs.lastSelectedPlatform
            _state.update { current ->
                recalculateSpec(
                    current.copy(
                        selectedPlatform = platform,
                        selectedAspectMode = prefs.lastSelectedAspectMode,
                        photoConfig = defaultPhotoConfig(platform, prefs),
                        videoConfig = defaultVideoConfig(platform, prefs),
                    ),
                )
            }
        }
    }

    private fun loadSources(
        uris: List<MediaUri>,
        initialPlatform: TargetPlatform?,
    ) {
        inspectJob?.cancel()
        optimizationJob?.cancel()
        if (uris.isEmpty()) {
            _state.update { StudioEditorUiState() }
            return
        }

        inspectJob =
            viewModelScope.launch {
                val prefs = readPreferencesSafely()
                cachedPreferences = prefs
                val platform = initialPlatform ?: prefs.lastSelectedPlatform

                _state.update {
                    StudioEditorUiState(
                        queue = uris.map { uri -> StudioQueueEntry(uri = uri, isLoading = true) },
                        activeIndex = 0,
                        selectedPlatform = platform,
                        selectedAspectMode = prefs.lastSelectedAspectMode,
                        cropAdjustment = CropAdjustment.CENTER,
                        blurRadiusPx = CanvasGeometry.DEFAULT_BLUR_RADIUS_PX,
                        photoConfig = defaultPhotoConfig(platform, prefs),
                        videoConfig = defaultVideoConfig(platform, prefs),
                    )
                }

                uris.forEachIndexed { index, uri ->
                    val result = metadataRepository.inspect(uri)
                    _state.update { current ->
                        val updatedQueue =
                            current.queue.mapIndexed { qIdx, entry ->
                                if (qIdx != index) {
                                    entry
                                } else {
                                    when (result) {
                                        is AppResult.Success ->
                                            entry.copy(
                                                metadata = result.value,
                                                isLoading = false,
                                                errorMessage = null,
                                            )
                                        is AppResult.Failure ->
                                            entry.copy(
                                                metadata = null,
                                                isLoading = false,
                                                errorMessage = result.error.toUserMessage(),
                                            )
                                    }
                                }
                            }
                        val nextError =
                            if (index == current.activeIndex && result is AppResult.Failure) {
                                result.error.toUserMessage()
                            } else {
                                current.errorMessage
                            }
                        recalculateSpec(current.copy(queue = updatedQueue, errorMessage = nextError))
                    }
                }
            }
    }

    private fun selectQueueIndex(index: Int) {
        _state.update { current ->
            if (index !in current.queue.indices || index == current.activeIndex) {
                current
            } else {
                recalculateSpec(current.copy(activeIndex = index, errorMessage = current.queue[index].errorMessage))
            }
        }
    }

    private fun selectPlatform(platform: TargetPlatform) {
        updateAndRecalculate { current ->
            current.copy(
                selectedPlatform = platform,
                photoConfig =
                    defaultPhotoConfig(platform, cachedPreferences).copy(
                        scalingAlgorithm = current.photoConfig.scalingAlgorithm,
                        targetResolution = current.photoConfig.targetResolution,
                        autoSrgbFix = current.photoConfig.autoSrgbFix,
                    ),
                videoConfig =
                    defaultVideoConfig(platform, cachedPreferences).copy(
                        resolution = current.videoConfig.resolution,
                        frameRateMode = current.videoConfig.frameRateMode,
                        encoder = current.videoConfig.encoder,
                        antiBlurSharpening = current.videoConfig.antiBlurSharpening,
                        hdrToSdr = current.videoConfig.hdrToSdr,
                    ),
            )
        }
    }

    private fun updateAndRecalculate(transform: (StudioEditorUiState) -> StudioEditorUiState) {
        _state.update { current -> recalculateSpec(transform(current)) }
    }

    private fun recalculateSpec(state: StudioEditorUiState): StudioEditorUiState {
        val activeMeta = state.activeEntry?.metadata ?: return state.copy(activeSpec = null)
        val request =
            OptimizationRequest(
                source = activeMeta,
                platform = state.selectedPlatform,
                aspectMode = state.selectedAspectMode,
                photoConfig = state.photoConfig,
                videoConfig = state.videoConfig,
                crop = state.cropAdjustment,
                blurRadiusPx = state.blurRadiusPx,
            )
        return state.copy(activeSpec = calculateOptimalSpec(request))
    }

    private fun runOptimization(
        shareAfterSave: Boolean,
        shareTarget: DirectShareTarget?,
    ) {
        val snapshot = _state.value
        val readyEntries = snapshot.queue.mapNotNull { it.metadata }
        if (readyEntries.isEmpty() || snapshot.isProcessing) return

        optimizationJob?.cancel()
        optimizationJob =
            viewModelScope.launch {
                persistUserDefaults(snapshot)
                val totalItems = readyEntries.size
                val allSaved = mutableListOf<OptimizedMediaItem>()
                var failedCount = 0
                var firstFailureMessage: String? = null

                _state.update {
                    it.copy(
                        processingState = StudioProcessingState.Running(0f, currentItemIndex = 1, totalItems = totalItems),
                        errorMessage = null,
                    )
                }

                try {
                    for ((idx, metadata) in readyEntries.withIndex()) {
                        val failedMessage = optimizeQueueEntry(snapshot, metadata, idx, totalItems, allSaved)
                        if (failedMessage != null) {
                            failedCount++
                            if (firstFailureMessage == null) {
                                firstFailureMessage = failedMessage
                            }
                        }
                    }
                    finalizeBatchResult(
                        allSaved = allSaved,
                        failedCount = failedCount,
                        firstFailureMessage = firstFailureMessage,
                        platform = snapshot.selectedPlatform,
                        shareAfterSave = shareAfterSave,
                        shareTarget = shareTarget,
                    )
                } catch (e: CancellationException) {
                    _state.update { it.copy(processingState = StudioProcessingState.Idle) }
                    throw e
                }
            }
    }

    private suspend fun finalizeBatchResult(
        allSaved: List<OptimizedMediaItem>,
        failedCount: Int,
        firstFailureMessage: String?,
        platform: TargetPlatform,
        shareAfterSave: Boolean,
        shareTarget: DirectShareTarget?,
    ) {
        when {
            failedCount == 0 -> {
                _state.update {
                    it.copy(
                        processingState = StudioProcessingState.Completed(allSaved.toList()),
                        errorMessage = null,
                    )
                }
                emitOptimizationEffect(allSaved, platform, shareAfterSave, shareTarget)
            }
            allSaved.isNotEmpty() -> {
                val detail = firstFailureMessage ?: "Sebagian file gagal diproses."
                val summary = "${allSaved.size} file tersimpan, $failedCount gagal: $detail"
                _state.update {
                    it.copy(
                        processingState =
                            StudioProcessingState.PartialSuccess(
                                savedItems = allSaved.toList(),
                                failedCount = failedCount,
                                failureMessage = summary,
                            ),
                        errorMessage = summary,
                    )
                }
            }
            else -> {
                _state.update {
                    it.copy(
                        processingState = StudioProcessingState.Idle,
                        errorMessage = firstFailureMessage,
                    )
                }
            }
        }
    }

    private suspend fun optimizeQueueEntry(
        snapshot: StudioEditorUiState,
        metadata: MediaMetadata,
        index: Int,
        totalItems: Int,
        allSaved: MutableList<OptimizedMediaItem>,
    ): String? {
        val request =
            OptimizationRequest(
                source = metadata,
                platform = snapshot.selectedPlatform,
                aspectMode = snapshot.selectedAspectMode,
                photoConfig = snapshot.photoConfig,
                videoConfig = snapshot.videoConfig,
                crop = if (index == snapshot.activeIndex) snapshot.cropAdjustment else CropAdjustment.CENTER,
                blurRadiusPx = snapshot.blurRadiusPx,
            )
        var failedMessage: String? = null
        optimizeMedia(request).collect { status ->
            when (status) {
                is OptimizationStatus.Rendering -> {
                    val overall = (index + status.progressFraction) / totalItems
                    _state.update {
                        it.copy(
                            processingState =
                                StudioProcessingState.Running(
                                    progressFraction = overall.coerceIn(0f, 1f),
                                    currentItemIndex = index + 1,
                                    totalItems = totalItems,
                                ),
                        )
                    }
                }
                is OptimizationStatus.SavingToGallery -> {
                    _state.update {
                        it.copy(
                            processingState =
                                StudioProcessingState.Running(
                                    progressFraction = ((index + 0.99f) / totalItems).coerceIn(0f, 1f),
                                    currentItemIndex = index + 1,
                                    totalItems = totalItems,
                                    savingPart = status.currentPart,
                                    totalParts = status.totalParts,
                                ),
                        )
                    }
                }
                is OptimizationStatus.Completed -> allSaved += status.items
                is OptimizationStatus.Failed -> failedMessage = status.error.toUserMessage()
            }
        }
        return failedMessage
    }

    private suspend fun emitOptimizationEffect(
        allSaved: List<OptimizedMediaItem>,
        platform: TargetPlatform,
        shareAfterSave: Boolean,
        shareTarget: DirectShareTarget?,
    ) {
        if (allSaved.isEmpty()) return
        if (shareAfterSave) {
            val target = shareTarget ?: DirectShareTarget.defaultFor(platform)
            _effect.send(StudioEditorUiEffect.LaunchShare(SharePayload.fromItems(allSaved, target)))
        } else {
            _effect.send(StudioEditorUiEffect.SavedSuccessfully(allSaved.toList()))
        }
    }

    private fun cancelOptimization() {
        optimizationJob?.cancel()
        optimizationJob = null
        _state.update { it.copy(processingState = StudioProcessingState.Idle) }
    }

    private suspend fun persistUserDefaults(snapshot: StudioEditorUiState) {
        preferencesRepository.setLastSelection(snapshot.selectedPlatform, snapshot.selectedAspectMode)
        preferencesRepository.setPreferredEncoder(snapshot.videoConfig.encoder)
        cachedPreferences =
            cachedPreferences.copy(
                lastSelectedPlatform = snapshot.selectedPlatform,
                lastSelectedAspectMode = snapshot.selectedAspectMode,
                preferredVideoEncoder = snapshot.videoConfig.encoder,
            )
    }

    private suspend fun readPreferencesSafely(): UserPreferences =
        try {
            preferencesRepository.observePreferences().firstOrNull() ?: UserPreferences()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            UserPreferences()
        }

    private fun defaultPhotoConfig(
        platform: TargetPlatform,
        prefs: UserPreferences,
    ): PhotoAdvancedConfig {
        val base = PhotoAdvancedConfig.defaultFor(platform)
        val sharpness = prefs.defaultSharpnessPercent
        return if (sharpness != null) base.copy(sharpnessPercent = sharpness) else base
    }

    private fun defaultVideoConfig(
        platform: TargetPlatform,
        prefs: UserPreferences,
    ): VideoAdvancedConfig = VideoAdvancedConfig.defaultFor(platform).copy(encoder = prefs.preferredVideoEncoder)

    companion object {
        const val KEY_SOURCE_URI: String = "source_uri"
        const val KEY_SOURCE_URIS: String = "source_uris"
        const val KEY_INITIAL_PLATFORM: String = "platform"

        private fun extractInitialUris(savedStateHandle: SavedStateHandle): List<MediaUri> {
            val many =
                savedStateHandle.get<List<String>>(KEY_SOURCE_URIS)
                    ?.filter { it.isNotBlank() }
                    ?.map(::MediaUri)
            if (!many.isNullOrEmpty()) return many

            val single = savedStateHandle.get<String>(KEY_SOURCE_URI)?.takeIf { it.isNotBlank() }
            return if (single != null) listOf(MediaUri(single)) else emptyList()
        }
    }
}
