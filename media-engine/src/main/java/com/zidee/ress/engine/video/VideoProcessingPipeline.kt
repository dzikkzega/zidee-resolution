package com.zidee.ress.engine.video

import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.engine.StagingDirectoryCleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Single-pass offline FFmpeg video optimization pipeline with real-time progress
 * emission and Auto-Split segment collection.
 */
class VideoProcessingPipeline(
    private val dispatchers: DispatcherProvider,
    private val executor: FfmpegCommandExecutor,
    private val stagingCleaner: StagingDirectoryCleaner,
    private val filtergraphBuilder: FfmpegFiltergraphBuilder = FfmpegFiltergraphBuilder(),
) {
    fun process(
        source: MediaUri,
        spec: OptimalMediaSpec,
    ): Flow<EngineProgress> =
        channelFlow {
            val videoSpec = requireNotNull(spec.video) { "VideoProcessingPipeline requires video spec" }
            val totalDurationMs = videoSpec.segmentDurationsMs.sum().takeIf { it > 0L }
            val jobDir: File = stagingCleaner.createJobDirectory()

            try {
                trySend(EngineProgress.Running(0.01f))
                val inputPath = executor.resolveInputPath(source.value)
                val targetPattern = StorySegmentSplitter.outputTargetFile(jobDir, videoSpec)
                val arguments =
                    filtergraphBuilder.buildArguments(
                        inputPath = inputPath,
                        outputPath = targetPattern.absolutePath,
                        spec = spec,
                    )

                var lastSent = 0.01f
                val result =
                    executor.execute(arguments) { elapsedMs ->
                        val fraction = FfmpegProgressParser.fractionFromElapsedMs(elapsedMs, totalDurationMs)
                        if (fraction != null && fraction > lastSent) {
                            lastSent = fraction.coerceAtMost(0.99f)
                            trySend(EngineProgress.Running(lastSent))
                        }
                    }

                check(result.returnCode == 0) {
                    "FFmpeg failed with exit code ${result.returnCode}: ${result.logs.takeLast(500)}"
                }

                val outputs = StorySegmentSplitter.collectOutputs(jobDir, spec.outputSize, videoSpec)
                trySend(EngineProgress.Running(1.0f))
                trySend(EngineProgress.Completed(outputs))
            } catch (e: CancellationException) {
                stagingCleaner.deleteDirectory(jobDir)
                throw e
            } catch (t: Throwable) {
                stagingCleaner.deleteDirectory(jobDir)
                throw t
            }
        }.flowOn(dispatchers.io)
}
