package com.zidee.ress.domain.repository

import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.StagedOutput
import kotlinx.coroutines.flow.Flow

/** Progress events from one render. */
sealed interface EngineProgress {
    /** [fraction] in 0..1. */
    data class Running(val fraction: Float) : EngineProgress

    /** All parts rendered to cache. Ownership of the files passes to the caller. */
    data class Completed(val outputs: List<StagedOutput>) : EngineProgress
}

/**
 * On-device photo/video renderer. Implemented in `:media-engine`.
 *
 * The flow is cold; collecting starts the render and cancelling the collector cancels it
 * and deletes partial cache files. Failures are thrown as exceptions from the flow.
 */
interface MediaOptimizerEngine {
    fun render(
        source: MediaUri,
        spec: OptimalMediaSpec,
    ): Flow<EngineProgress>
}
