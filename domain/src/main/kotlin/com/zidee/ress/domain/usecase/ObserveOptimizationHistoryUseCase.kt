package com.zidee.ress.domain.usecase

import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.repository.MediaStorageRepository
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes saved items in `ZideeResolution` newest-first, filtering out any entry the user
 * manually deleted via the system gallery and pruning stale DB records in the background.
 */
class ObserveOptimizationHistoryUseCase(
    private val history: OptimizationHistoryRepository,
    private val storage: MediaStorageRepository,
) {
    operator fun invoke(): Flow<List<OptimizedMediaItem>> =
        history.observeHistory().map { items ->
            val alive = ArrayList<OptimizedMediaItem>(items.size)
            for (item in items) {
                if (storage.exists(item.mediaStoreUri)) {
                    alive += item
                } else {
                    history.delete(item.id)
                }
            }
            alive
        }
}
