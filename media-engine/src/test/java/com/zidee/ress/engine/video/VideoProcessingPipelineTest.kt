package com.zidee.ress.engine.video

import app.cash.turbine.test
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.engine.StagingDirectoryCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VideoProcessingPipelineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val calculate = CalculateOptimalSpecUseCase()

    @Test
    fun `emits real time progress and collects auto split segments in order`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val dispatchers = TestDispatchers(dispatcher)
            val cleaner = StagingDirectoryCleaner { tempFolder.root }

            val fakeExecutor =
                object : FfmpegCommandExecutor {
                    override fun resolveInputPath(sourceUri: String): String = "/fake/source.mp4"

                    override suspend fun execute(
                        arguments: List<String>,
                        onElapsedMs: (Double) -> Unit,
                    ): FfmpegExecutionResult {
                        onElapsedMs(15_000.0)
                        onElapsedMs(45_000.0)
                        val outPattern = File(arguments.last())
                        val dir = requireNotNull(outPattern.parentFile)
                        File(dir, "part_00.mp4").writeBytes(ByteArray(100))
                        File(dir, "part_01.mp4").writeBytes(ByteArray(120))
                        File(dir, "part_02.mp4").writeBytes(ByteArray(60))
                        return FfmpegExecutionResult(returnCode = 0, logs = "ok")
                    }
                }

            val spec =
                calculate(
                    OptimizationRequest(
                        source =
                            MediaMetadata(
                                uri = MediaUri("content://gallery/video/1"),
                                mediaType = MediaType.VIDEO,
                                width = 1080,
                                height = 1920,
                                sizeBytes = 10_000_000,
                                durationMs = 75_000,
                                frameRate = 30.0,
                            ),
                        platform = TargetPlatform.WA_STORY,
                        aspectMode = CanvasAspectMode.ORIGINAL,
                    ),
                )
            val pipeline = VideoProcessingPipeline(dispatchers, fakeExecutor, cleaner)

            pipeline.process(MediaUri("content://gallery/video/1"), spec).test {
                assertEquals(EngineProgress.Running(0.01f), awaitItem())
                assertEquals(EngineProgress.Running(0.2f), awaitItem())
                assertEquals(EngineProgress.Running(0.6f), awaitItem())
                assertEquals(EngineProgress.Running(1.0f), awaitItem())

                val completed = awaitItem() as EngineProgress.Completed
                assertEquals(3, completed.outputs.size)
                assertEquals(listOf(1, 2, 3), completed.outputs.map { it.partIndex })
                assertEquals(listOf(3, 3, 3), completed.outputs.map { it.totalParts })
                assertEquals(listOf(30_000L, 30_000L, 15_000L), completed.outputs.map { it.durationMs })
                awaitComplete()
            }
        }

    @Test
    fun `deletes job staging directory when ffmpeg returns non zero exit code`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val dispatchers = TestDispatchers(dispatcher)
            val cleaner = StagingDirectoryCleaner { tempFolder.root }

            val failingExecutor =
                object : FfmpegCommandExecutor {
                    override fun resolveInputPath(sourceUri: String): String = "/fake/source.mp4"

                    override suspend fun execute(
                        arguments: List<String>,
                        onElapsedMs: (Double) -> Unit,
                    ): FfmpegExecutionResult {
                        val dir = requireNotNull(File(arguments.last()).parentFile)
                        File(dir, "output.mp4").writeBytes(ByteArray(16))
                        return FfmpegExecutionResult(returnCode = 1, logs = "encoder error")
                    }
                }

            val spec =
                calculate(
                    OptimizationRequest(
                        source =
                            MediaMetadata(
                                uri = MediaUri("content://gallery/video/2"),
                                mediaType = MediaType.VIDEO,
                                width = 1920,
                                height = 1080,
                                sizeBytes = 5_000_000,
                                durationMs = 10_000,
                            ),
                        platform = TargetPlatform.WA_CHAT,
                        aspectMode = CanvasAspectMode.ORIGINAL,
                    ),
                )
            val pipeline = VideoProcessingPipeline(dispatchers, failingExecutor, cleaner)

            pipeline.process(MediaUri("content://gallery/video/2"), spec).test {
                assertEquals(EngineProgress.Running(0.01f), awaitItem())
                val error = awaitError()
                assertTrue(error.message?.contains("exit code 1") == true)
            }

            val stagingRoot = File(tempFolder.root, "zidee_staging")
            assertTrue(stagingRoot.listFiles().orEmpty().isEmpty())
        }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
}
