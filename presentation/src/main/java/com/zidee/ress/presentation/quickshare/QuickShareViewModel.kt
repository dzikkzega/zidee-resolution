package com.zidee.ress.presentation.quickshare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
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

data class QuickShareUiState(
    val sourceUris: List<MediaUri> = emptyList(),
    val inspectedSources: List<MediaMetadata> = emptyList(),
    val isInspecting: Boolean = false,
    val selectedPlatform: TargetPlatform = TargetPlatform.WA_STORY,
    val selectedAspectMode: CanvasAspectMode = CanvasAspectMode.BLUR_BG_9_16,
    val photoConfig: PhotoAdvancedConfig = PhotoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY),
    val videoConfig: VideoAdvancedConfig = VideoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY),
    val activeSpec: OptimalMediaSpec? = null,
    val isAdvancedExpanded: Boolean = false,
    val isProcessing: Boolean = false,
    val progressFraction: Float = 0f,
    val currentItemIndex: Int = 0,
    val totalItems: Int = 0,
    val savingPart: Int? = null,
    val totalParts: Int? = null,
    val completedItems: List<OptimizedMediaItem> = emptyList(),
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean get() = inspectedSources.isNotEmpty() && !isInspecting && !isProcessing
}

sealed interface QuickShareUiEvent {
    data class LoadSources(val uris: List<MediaUri>) : QuickShareUiEvent

    data class SelectPlatform(val platform: TargetPlatform) : QuickShareUiEvent

    data class SelectAspectMode(val mode: CanvasAspectMode) : QuickShareUiEvent

    data object ToggleAdvanced : QuickShareUiEvent

    data class UpdatePhotoConfig(val transform: (PhotoAdvancedConfig) -> PhotoAdvancedConfig) : QuickShareUiEvent

    data class UpdateVideoConfig(val transform: (VideoAdvancedConfig) -> VideoAdvancedConfig) : QuickShareUiEvent

    data object ClickSaveOnly : QuickShareUiEvent

    data class ClickSaveAndShare(val target: DirectShareTarget? = null) : QuickShareUiEvent

    data object ClickCancel : QuickShareUiEvent

    data object DismissError : QuickShareUiEvent
}

sealed interface QuickShareUiEffect {
    data class LaunchTargetAppShare(val payload: SharePayload) : QuickShareUiEffect

    data class SavedOnlyCompleted(val items: List<OptimizedMediaItem>) : QuickShareUiEffect

    data object DismissSheet : QuickShareUiEffect
}

/**
 * State holder for `QuickShareActivity` (Gallery Share Bottom Sheet).
 * Always saves permanently into `Pictures/ZideeResolution` or `Movies/ZideeResolution`
 * before emitting a direct WA/IG share intent.
 */
class QuickShareViewModel(
    private val calculateOptimalSpec: CalculateOptimalSpecUseCase,
    private val optimizeMedia: OptimizeMediaUseCase,
    private val metadataRepository: MediaMetadataRepository,
    private val preferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(QuickShareUiState())
    val state: StateFlow<QuickShareUiState> = _state.asStateFlow()

    private val _effect = Channel<QuickShareUiEffect>(Channel.BUFFERED)
    val effect: Flow<QuickShareUiEffect> = _effect.receiveAsFlow()

    private var cachedPreferences: UserPreferences = UserPreferences()
    private var inspectJob: Job? = null
    private var processJob: Job? = null

    init {
        val initialUris = extractInitialUris(savedStateHandle)
        if (initialUris.isNotEmpty()) {
            loadSources(initialUris)
        } else {
            hydratePreferences()
        }
    }

    fun onEvent(event: QuickShareUiEvent) {
        when (event) {
            is QuickShareUiEvent.LoadSources -> loadSources(event.uris)
            is QuickShareUiEvent.SelectPlatform -> selectPlatform(event.platform)
            is QuickShareUiEvent.SelectAspectMode ->
                updateAndRecalculate {
                    it.copy(selectedAspectMode = event.mode)
                }
            QuickShareUiEvent.ToggleAdvanced ->
                _state.update {
                    it.copy(isAdvancedExpanded = !it.isAdvancedExpanded)
                }
            is QuickShareUiEvent.UpdatePhotoConfig ->
                updateAndRecalculate {
                    it.copy(photoConfig = event.transform(it.photoConfig))
                }
            is QuickShareUiEvent.UpdateVideoConfig ->
                updateAndRecalculate {
                    it.copy(videoConfig = event.transform(it.videoConfig))
                }
            QuickShareUiEvent.ClickSaveOnly -> executeOptimization(shareAfterSave = false, shareTarget = null)
            is QuickShareUiEvent.ClickSaveAndShare -> executeOptimization(shareAfterSave = true, shareTarget = event.target)
            QuickShareUiEvent.ClickCancel -> {
                processJob?.cancel()
                processJob = null
                _state.update { it.copy(isProcessing = false) }
                _effect.trySend(QuickShareUiEffect.DismissSheet)
            }
            QuickShareUiEvent.DismissError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun hydratePreferences() {
        viewModelScope.launch {
            val prefs = readPreferencesSafely()
            cachedPreferences = prefs
            _state.update { current ->
                recalculateSpec(
                    current.copy(
                        selectedPlatform = prefs.lastSelectedPlatform,
                        selectedAspectMode = prefs.lastSelectedAspectMode,
                        photoConfig = defaultPhotoConfig(prefs.lastSelectedPlatform, prefs),
                        videoConfig = defaultVideoConfig(prefs.lastSelectedPlatform, prefs),
                    ),
                )
            }
        }
    }

    private fun loadSources(uris: List<MediaUri>) {
        inspectJob?.cancel()
        processJob?.cancel()
        if (uris.isEmpty()) {
            _state.update {
                it.copy(
                    sourceUris = emptyList(),
                    inspectedSources = emptyList(),
                    isInspecting = false,
                    activeSpec = null,
                    errorMessage = "Tidak ada media yang dibagikan.",
                )
            }
            return
        }

        inspectJob =
            viewModelScope.launch {
                val prefs = readPreferencesSafely()
                cachedPreferences = prefs

                _state.update {
                    QuickShareUiState(
                        sourceUris = uris,
                        isInspecting = true,
                        selectedPlatform = prefs.lastSelectedPlatform,
                        selectedAspectMode = prefs.lastSelectedAspectMode,
                        photoConfig = defaultPhotoConfig(prefs.lastSelectedPlatform, prefs),
                        videoConfig = defaultVideoConfig(prefs.lastSelectedPlatform, prefs),
                    )
                }

                val inspected = mutableListOf<MediaMetadata>()
                var firstFailureMessage: String? = null
                for (uri in uris) {
                    when (val result = metadataRepository.inspect(uri)) {
                        is AppResult.Success -> inspected += result.value
                        is AppResult.Failure ->
                            if (firstFailureMessage == null) {
                                firstFailureMessage = result.error.toUserMessage()
                            }
                    }
                }

                _state.update { current ->
                    recalculateSpec(
                        current.copy(
                            inspectedSources = inspected,
                            isInspecting = false,
                            errorMessage = if (inspected.isEmpty()) firstFailureMessage else null,
                        ),
                    )
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

    private fun updateAndRecalculate(transform: (QuickShareUiState) -> QuickShareUiState) {
        _state.update { current -> recalculateSpec(transform(current)) }
    }

    private fun recalculateSpec(state: QuickShareUiState): QuickShareUiState {
        val primary = state.inspectedSources.firstOrNull() ?: return state.copy(activeSpec = null)
        val request =
            OptimizationRequest(
                source = primary,
                platform = state.selectedPlatform,
                aspectMode = state.selectedAspectMode,
                photoConfig = state.photoConfig,
                videoConfig = state.videoConfig,
                crop = CropAdjustment.CENTER,
            )
        return state.copy(activeSpec = calculateOptimalSpec(request))
    }

    private fun executeOptimization(
        shareAfterSave: Boolean,
        shareTarget: DirectShareTarget?,
    ) {
        val snapshot = _state.value
        if (snapshot.inspectedSources.isEmpty() || snapshot.isProcessing) return

        processJob?.cancel()
        processJob =
            viewModelScope.launch {
                persistSelectionDefaults(snapshot)
                val total = snapshot.inspectedSources.size
                val savedItems = mutableListOf<OptimizedMediaItem>()
                var failedCount = 0
                var firstFailureMessage: String? = null
                _state.update {
                    it.copy(
                        isProcessing = true,
                        progressFraction = 0f,
                        currentItemIndex = 1,
                        totalItems = total,
                        savingPart = null,
                        totalParts = null,
                        errorMessage = null,
                    )
                }

                try {
                    for ((idx, meta) in snapshot.inspectedSources.withIndex()) {
                        val failureMessage = optimizeSingleSource(snapshot, meta, idx, total, savedItems)
                        if (failureMessage != null) {
                            failedCount++
                            if (firstFailureMessage == null) {
                                firstFailureMessage = failureMessage
                            }
                        }
                    }
                    finalizeBatchResult(
                        savedItems = savedItems,
                        failedCount = failedCount,
                        firstFailureMessage = firstFailureMessage,
                        platform = snapshot.selectedPlatform,
                        shareAfterSave = shareAfterSave,
                        shareTarget = shareTarget,
                    )
                } catch (e: CancellationException) {
                    _state.update { it.copy(isProcessing = false) }
                    throw e
                }
            }
    }

    private suspend fun finalizeBatchResult(
        savedItems: List<OptimizedMediaItem>,
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
                        isProcessing = false,
                        progressFraction = 1f,
                        completedItems = savedItems.toList(),
                        errorMessage = null,
                    )
                }
                emitShareOrSavedEffect(savedItems, platform, shareAfterSave, shareTarget)
            }
            savedItems.isNotEmpty() -> {
                val detail = firstFailureMessage ?: "Sebagian file gagal diproses."
                val summary = "${savedItems.size} file tersimpan, $failedCount gagal: $detail"
                _state.update {
                    it.copy(
                        isProcessing = false,
                        progressFraction = 1f,
                        completedItems = savedItems.toList(),
                        errorMessage = summary,
                    )
                }
            }
            else -> {
                _state.update { it.copy(isProcessing = false, errorMessage = firstFailureMessage) }
            }
        }
    }

    private suspend fun persistSelectionDefaults(snapshot: QuickShareUiState) {
        preferencesRepository.setLastSelection(snapshot.selectedPlatform, snapshot.selectedAspectMode)
        preferencesRepository.setPreferredEncoder(snapshot.videoConfig.encoder)
        cachedPreferences =
            cachedPreferences.copy(
                lastSelectedPlatform = snapshot.selectedPlatform,
                lastSelectedAspectMode = snapshot.selectedAspectMode,
                preferredVideoEncoder = snapshot.videoConfig.encoder,
            )
    }

    private suspend fun optimizeSingleSource(
        snapshot: QuickShareUiState,
        meta: MediaMetadata,
        index: Int,
        total: Int,
        savedItems: MutableList<OptimizedMediaItem>,
    ): String? {
        val request =
            OptimizationRequest(
                source = meta,
                platform = snapshot.selectedPlatform,
                aspectMode = snapshot.selectedAspectMode,
                photoConfig = snapshot.photoConfig,
                videoConfig = snapshot.videoConfig,
                crop = CropAdjustment.CENTER,
            )
        var failureMessage: String? = null
        optimizeMedia(request).collect { status ->
            when (status) {
                is OptimizationStatus.Rendering -> {
                    val overall = (index + status.progressFraction) / total
                    _state.update {
                        it.copy(
                            progressFraction = overall.coerceIn(0f, 1f),
                            currentItemIndex = index + 1,
                            totalItems = total,
                        )
                    }
                }
                is OptimizationStatus.SavingToGallery -> {
                    _state.update {
                        it.copy(
                            progressFraction = ((index + 0.99f) / total).coerceIn(0f, 1f),
                            currentItemIndex = index + 1,
                            totalItems = total,
                            savingPart = status.currentPart,
                            totalParts = status.totalParts,
                        )
                    }
                }
                is OptimizationStatus.Completed -> savedItems += status.items
                is OptimizationStatus.Failed -> failureMessage = status.error.toUserMessage()
            }
        }
        return failureMessage
    }

    private suspend fun emitShareOrSavedEffect(
        savedItems: List<OptimizedMediaItem>,
        platform: TargetPlatform,
        shareAfterSave: Boolean,
        shareTarget: DirectShareTarget?,
    ) {
        if (savedItems.isEmpty()) return
        if (shareAfterSave) {
            val target = shareTarget ?: DirectShareTarget.defaultFor(platform)
            _effect.send(QuickShareUiEffect.LaunchTargetAppShare(SharePayload.fromItems(savedItems, target)))
        } else {
            _effect.send(QuickShareUiEffect.SavedOnlyCompleted(savedItems.toList()))
        }
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
