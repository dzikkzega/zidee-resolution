package com.zidee.ress.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.repository.MediaStorageRepository
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.repository.UserPreferencesRepository
import com.zidee.ress.domain.usecase.ObserveOptimizationHistoryUseCase
import com.zidee.ress.presentation.common.toUserMessage
import com.zidee.ress.presentation.share.DirectShareTarget
import com.zidee.ress.presentation.share.SharePayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoadingHistory: Boolean = true,
    val historyItems: List<OptimizedMediaItem> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val errorMessage: String? = null,
)

sealed interface DashboardUiEvent {
    data class SelectPlatformShortcut(val platform: TargetPlatform) : DashboardUiEvent

    data class MediaPicked(val uris: List<MediaUri>, val platform: TargetPlatform? = null) : DashboardUiEvent

    data class ReShareItem(
        val item: OptimizedMediaItem,
        val target: DirectShareTarget? = null,
    ) : DashboardUiEvent

    data class ReShareItems(
        val items: List<OptimizedMediaItem>,
        val target: DirectShareTarget? = null,
    ) : DashboardUiEvent

    data class DeleteHistoryItem(val id: String) : DashboardUiEvent

    data object DismissError : DashboardUiEvent
}

sealed interface DashboardUiEffect {
    data class OpenMediaPicker(val platform: TargetPlatform) : DashboardUiEffect

    data class NavigateToStudio(
        val sourceUris: List<MediaUri>,
        val initialPlatform: TargetPlatform,
    ) : DashboardUiEffect

    data class LaunchShare(val payload: SharePayload) : DashboardUiEffect

    data class ShowMessage(val message: String) : DashboardUiEffect
}

/**
 * Coordinates the main Home Dashboard: live `ZideeResolution` gallery album history,
 * platform shortcuts, photo/video picker handoff to Studio, and 1-Click Re-Share without re-encoding.
 */
class DashboardViewModel(
    observeOptimizationHistory: ObserveOptimizationHistoryUseCase,
    private val storageRepository: MediaStorageRepository,
    private val historyRepository: OptimizationHistoryRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val _effect = Channel<DashboardUiEffect>(Channel.BUFFERED)
    val effect: Flow<DashboardUiEffect> = _effect.receiveAsFlow()

    init {
        observeOptimizationHistory()
            .onEach { items ->
                _state.update { it.copy(isLoadingHistory = false, historyItems = items) }
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                _state.update {
                    it.copy(
                        isLoadingHistory = false,
                        errorMessage = "Gagal memuat riwayat album ZideeResolution.",
                    )
                }
            }
            .launchIn(viewModelScope)

        preferencesRepository.observePreferences()
            .onEach { prefs ->
                _state.update { it.copy(preferences = prefs) }
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            is DashboardUiEvent.SelectPlatformShortcut -> {
                _effect.trySend(DashboardUiEffect.OpenMediaPicker(event.platform))
            }
            is DashboardUiEvent.MediaPicked -> handleMediaPicked(event.uris, event.platform)
            is DashboardUiEvent.ReShareItem -> handleReShare(listOf(event.item), event.target)
            is DashboardUiEvent.ReShareItems -> handleReShare(event.items, event.target)
            is DashboardUiEvent.DeleteHistoryItem -> handleDeleteHistoryItem(event.id)
            DashboardUiEvent.DismissError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun handleMediaPicked(
        uris: List<MediaUri>,
        requestedPlatform: TargetPlatform?,
    ) {
        if (uris.isEmpty()) return
        val platform = requestedPlatform ?: _state.value.preferences.lastSelectedPlatform
        _effect.trySend(DashboardUiEffect.NavigateToStudio(sourceUris = uris, initialPlatform = platform))
    }

    private fun handleReShare(
        items: List<OptimizedMediaItem>,
        requestedTarget: DirectShareTarget?,
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val surviving = mutableListOf<OptimizedMediaItem>()
            for (item in items) {
                if (storageRepository.exists(item.mediaStoreUri)) {
                    surviving += item
                } else {
                    historyRepository.delete(item.id)
                }
            }

            if (surviving.isEmpty()) {
                val msg = "File sudah dihapus dari folder Galeri ZideeResolution."
                _state.update { it.copy(errorMessage = msg) }
                _effect.send(DashboardUiEffect.ShowMessage(msg))
                return@launch
            }

            val target = requestedTarget ?: DirectShareTarget.defaultFor(surviving.first().platform)
            _effect.send(DashboardUiEffect.LaunchShare(SharePayload.fromItems(surviving, target)))
        }
    }

    private fun handleDeleteHistoryItem(id: String) {
        viewModelScope.launch {
            when (val result = historyRepository.delete(id)) {
                is AppResult.Failure -> _state.update { it.copy(errorMessage = result.error.toUserMessage()) }
                is AppResult.Success -> Unit
            }
        }
    }
}
