package com.zidee.ress.engine

import android.content.Context
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.repository.MediaOptimizerEngine
import com.zidee.ress.engine.photo.PhotoProcessingPipeline
import com.zidee.ress.engine.service.MediaOptimizationForegroundService
import com.zidee.ress.engine.video.VideoProcessingPipeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt

/** Hook so the optimizer can keep a foreground notification alive without tying tests to Android Service APIs. */
interface ForegroundExecutionMonitor {
    fun begin(
        job: Job?,
        label: String,
    ): Int

    fun onProgress(
        sessionId: Int,
        label: String,
        percent: Int,
    )

    fun end(sessionId: Int)
}

class AndroidForegroundExecutionMonitor(
    private val context: Context,
) : ForegroundExecutionMonitor {
    override fun begin(
        job: Job?,
        label: String,
    ): Int {
        val id = MediaOptimizationForegroundService.registerJob(job)
        MediaOptimizationForegroundService.startOrUpdate(context, label, 0)
        return id
    }

    override fun onProgress(
        sessionId: Int,
        label: String,
        percent: Int,
    ) {
        MediaOptimizationForegroundService.updateProgressNotification(context, label, percent)
    }

    override fun end(sessionId: Int) {
        if (sessionId >= 0) {
            MediaOptimizationForegroundService.unregisterJob(sessionId)
        }
        MediaOptimizationForegroundService.stopIfIdle(context)
    }
}

/**
 * [MediaOptimizerEngine] implementation routing photos to [PhotoProcessingPipeline] and
 * videos to [VideoProcessingPipeline] under [ForegroundExecutionMonitor] protection.
 */
class OfflineMediaOptimizerEngine(
    private val photoFlowFactory: (MediaUri, OptimalMediaSpec) -> Flow<EngineProgress>,
    private val videoFlowFactory: (MediaUri, OptimalMediaSpec) -> Flow<EngineProgress>,
    private val foregroundMonitor: ForegroundExecutionMonitor,
    private val stagingCleaner: StagingDirectoryCleaner,
) : MediaOptimizerEngine {
    constructor(
        context: Context,
        photoPipeline: PhotoProcessingPipeline,
        videoPipeline: VideoProcessingPipeline,
        stagingCleaner: StagingDirectoryCleaner = StagingDirectoryCleaner(context),
        foregroundMonitor: ForegroundExecutionMonitor = AndroidForegroundExecutionMonitor(context),
    ) : this(
        photoFlowFactory = photoPipeline::process,
        videoFlowFactory = videoPipeline::process,
        foregroundMonitor = foregroundMonitor,
        stagingCleaner = stagingCleaner,
    )

    override fun render(
        source: MediaUri,
        spec: OptimalMediaSpec,
    ): Flow<EngineProgress> =
        flow {
            stagingCleaner.deleteOrphanedOlderThan()

            val isVideo = spec.mediaType == MediaType.VIDEO
            val label = if (isVideo) "Mengoptimalkan video ${spec.platform.name}" else "Mengoptimalkan foto ${spec.platform.name}"
            var foregroundBegun = false
            var sessionId = -1

            try {
                if (isVideo) {
                    sessionId = foregroundMonitor.begin(currentCoroutineContext()[Job], label)
                    foregroundBegun = true
                }

                val delegate =
                    if (isVideo) {
                        videoFlowFactory(source, spec)
                    } else {
                        photoFlowFactory(source, spec)
                    }
                var lastReportedPercent = -1
                delegate.collect { progress ->
                    if (isVideo && progress is EngineProgress.Running) {
                        val percent = (progress.fraction * 100f).roundToInt().coerceIn(0, 100)
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            foregroundMonitor.onProgress(sessionId, label, percent)
                        }
                    }
                    emit(progress)
                }
            } finally {
                if (foregroundBegun) {
                    foregroundMonitor.end(sessionId)
                }
            }
        }
}
