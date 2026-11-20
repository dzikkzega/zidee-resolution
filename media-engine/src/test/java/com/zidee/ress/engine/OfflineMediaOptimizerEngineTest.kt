package com.zidee.ress.engine

import app.cash.turbine.test
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineMediaOptimizerEngineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val calculate = CalculateOptimalSpecUseCase()

    @Test
    fun `video render starts and stops foreground monitor with percent updates`() =
        runTest {
            val monitor = RecordingMonitor()
            val cleaner = StagingDirectoryCleaner { tempFolder.root }
            val expectedOutput = StagedOutput("/tmp/out.mp4", MediaType.VIDEO, PixelSize(1080, 1920), 2048, 10_000, 1, 1)

            val engine =
                OfflineMediaOptimizerEngine(
                    photoFlowFactory = { _, _ -> error("photo pipeline should not run") },
                    videoFlowFactory = { _, _ ->
                        flow {
                            emit(EngineProgress.Running(0.25f))
                            emit(EngineProgress.Running(0.75f))
                            emit(EngineProgress.Completed(listOf(expectedOutput)))
                        }
                    },
                    foregroundMonitor = monitor,
                    stagingCleaner = cleaner,
                )

            val spec =
                calculate(
                    OptimizationRequest(
                        source = MediaMetadata(MediaUri("content://v/1"), MediaType.VIDEO, 1080, 1920, 1_000_000, durationMs = 10_000),
                        platform = TargetPlatform.WA_STORY,
                        aspectMode = CanvasAspectMode.ORIGINAL,
                    ),
                )

            engine.render(MediaUri("content://v/1"), spec).test {
                assertEquals(EngineProgress.Running(0.25f), awaitItem())
                assertEquals(EngineProgress.Running(0.75f), awaitItem())
                assertEquals(EngineProgress.Completed(listOf(expectedOutput)), awaitItem())
                awaitComplete()
            }

            assertTrue(monitor.started)
            assertTrue(monitor.ended)
            assertEquals(listOf(25, 75), monitor.percents)
        }

    @Test
    fun `ends foreground session even when monitor returns sentinel id or pipeline throws`() =
        runTest {
            val monitor = RecordingMonitor(returnedSessionId = -1)
            val cleaner = StagingDirectoryCleaner { tempFolder.root }

            val engine =
                OfflineMediaOptimizerEngine(
                    photoFlowFactory = { _, _ -> error("photo pipeline should not run") },
                    videoFlowFactory = { _, _ ->
                        flow {
                            emit(EngineProgress.Running(0.10f))
                            error("FFmpeg aborted")
                        }
                    },
                    foregroundMonitor = monitor,
                    stagingCleaner = cleaner,
                )

            val spec =
                calculate(
                    OptimizationRequest(
                        source = MediaMetadata(MediaUri("content://v/2"), MediaType.VIDEO, 1080, 1920, 1_000_000, durationMs = 10_000),
                        platform = TargetPlatform.WA_STORY,
                        aspectMode = CanvasAspectMode.ORIGINAL,
                    ),
                )

            var failure: Throwable? = null
            try {
                engine.render(MediaUri("content://v/2"), spec).collect()
            } catch (t: Throwable) {
                failure = t
            }

            assertTrue(failure is IllegalStateException)
            assertTrue(monitor.started)
            assertTrue("end() must run in finally even when sessionId == -1", monitor.ended)
            assertEquals(-1, monitor.endedSessionId)
        }

    @Test
    fun `ends foreground session when video collection cancels early`() =
        runTest {
            val monitor = RecordingMonitor(returnedSessionId = 42)
            val cleaner = StagingDirectoryCleaner { tempFolder.root }

            val engine =
                OfflineMediaOptimizerEngine(
                    photoFlowFactory = { _, _ -> error("photo pipeline should not run") },
                    videoFlowFactory = { _, _ ->
                        flow {
                            emit(EngineProgress.Running(0.20f))
                            emit(EngineProgress.Running(0.50f))
                        }
                    },
                    foregroundMonitor = monitor,
                    stagingCleaner = cleaner,
                )

            val spec =
                calculate(
                    OptimizationRequest(
                        source = MediaMetadata(MediaUri("content://v/3"), MediaType.VIDEO, 1080, 1920, 1_000_000, durationMs = 10_000),
                        platform = TargetPlatform.WA_STORY,
                        aspectMode = CanvasAspectMode.ORIGINAL,
                    ),
                )

            engine.render(MediaUri("content://v/3"), spec).test {
                assertEquals(EngineProgress.Running(0.20f), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(monitor.started)
            assertTrue(monitor.ended)
            assertEquals(42, monitor.endedSessionId)
        }

    @Test
    fun `photo render skips foreground service and sweeps old staging orphans`() =
        runTest {
            val monitor = RecordingMonitor()
            val cleaner = StagingDirectoryCleaner { tempFolder.root }
            val staleFile =
                cleaner.createStagingFile("jpg").apply {
                    writeBytes(ByteArray(8))
                    setLastModified(System.currentTimeMillis() - 3 * 60 * 60 * 1000L)
                }
            val expectedOutput = StagedOutput("/tmp/out.jpg", MediaType.PHOTO, PixelSize(1080, 810), 512, null, 1, 1)

            val engine =
                OfflineMediaOptimizerEngine(
                    photoFlowFactory = { _, _ ->
                        flow {
                            emit(EngineProgress.Running(1.0f))
                            emit(EngineProgress.Completed(listOf(expectedOutput)))
                        }
                    },
                    videoFlowFactory = { _, _ -> error("video pipeline should not run") },
                    foregroundMonitor = monitor,
                    stagingCleaner = cleaner,
                )

            val spec =
                calculate(
                    OptimizationRequest(
                        source = MediaMetadata(MediaUri("content://p/1"), MediaType.PHOTO, 4000, 3000, 2_000_000),
                        platform = TargetPlatform.IG_STORY,
                        aspectMode = CanvasAspectMode.ORIGINAL,
                    ),
                )

            engine.render(MediaUri("content://p/1"), spec).test {
                assertEquals(EngineProgress.Running(1.0f), awaitItem())
                assertEquals(EngineProgress.Completed(listOf(expectedOutput)), awaitItem())
                awaitComplete()
            }

            assertFalse(monitor.started)
            assertFalse(File(staleFile.absolutePath).exists())
        }

    private class RecordingMonitor(
        private val returnedSessionId: Int = 1,
    ) : ForegroundExecutionMonitor {
        var started = false
        var ended = false
        var endedSessionId: Int? = null
        val percents = mutableListOf<Int>()

        override fun begin(
            job: Job?,
            label: String,
        ): Int {
            started = true
            return returnedSessionId
        }

        override fun onProgress(
            sessionId: Int,
            label: String,
            percent: Int,
        ) {
            percents += percent
        }

        override fun end(sessionId: Int) {
            ended = true
            endedSessionId = sessionId
        }
    }
}
