package com.zidee.ress.domain.repository

import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.StagedOutput
import kotlinx.coroutines.flow.Flow

/**
 * Permanent gallery storage in `Pictures/ZideeResolution` and `Movies/ZideeResolution`.
 * Implemented in `:data` with the MediaStore `IS_PENDING` protocol.
 */
interface MediaStorageRepository {
    /**
     * Copies [staged] into the album under [displayName] and deletes the cache file.
     * On failure nothing is left behind in the gallery.
     */
    suspend fun saveToGallery(
        staged: StagedOutput,
        displayName: String,
    ): AppResult<MediaUri>

    /** Deletes an unsaved staged cache file (used when multi-part saving aborts or cancels). */
    suspend fun discardStaged(staged: StagedOutput)

    /** False when the user deleted the file from the system gallery. */
    suspend fun exists(uri: MediaUri): Boolean
}

/** Local history of saved outputs, newest first. Implemented in `:data` with Room. */
interface OptimizationHistoryRepository {
    fun observeHistory(): Flow<List<OptimizedMediaItem>>

    suspend fun record(items: List<OptimizedMediaItem>): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>
}
