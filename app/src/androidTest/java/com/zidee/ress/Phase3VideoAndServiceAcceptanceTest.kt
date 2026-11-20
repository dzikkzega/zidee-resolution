package com.zidee.ress

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.zidee.ress.core.coroutines.DefaultDispatcherProvider
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.data.mediastore.ZideeMediaStoreSaver
import com.zidee.ress.domain.model.AutoSplitMode
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.FrameRateMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoAdvancedConfig
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.domain.usecase.OptimizationStatus
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import com.zidee.ress.engine.OfflineMediaOptimizerEngine
import com.zidee.ress.engine.StagingDirectoryCleaner
import com.zidee.ress.engine.photo.PhotoProcessingPipeline
import com.zidee.ress.engine.service.MediaOptimizationForegroundService
import com.zidee.ress.engine.video.FFmpegKitCommandExecutor
import com.zidee.ress.engine.video.VideoProcessingPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class Phase3VideoAndServiceAcceptanceTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val calculate = CalculateOptimalSpecUseCase()

    @Test
    fun t32_hdrVideoToSdrAndDualEncodersWithAutoSplit30sAnd60s() =
        runBlocking {
            val workDir = File(checkNotNull(context.externalCacheDir), "phase3_video_work").apply { mkdirs() }
            val report = StringBuilder()
            report.appendLine(
                "=== T-3.2 Video HDR->SDR, Dual Encoders & 30s/60s Auto-Split " +
                    "(${Build.MODEL}, Android ${Build.VERSION.RELEASE}) ===",
            )

            try {
                val hdrInput = File(workDir, "input_hdr10_1080p60.mp4")
                generateHdr10Input(hdrInput)
                val hdrProbe = probeVideo(hdrInput)
                report.appendLine("HDR Input Probe: $hdrProbe")
                assertEquals(1_920, hdrProbe.width)
                assertEquals(1_080, hdrProbe.height)
                assertTrue(
                    "Expected bt2020 primaries on HDR input, got ${hdrProbe.colorPrimaries}",
                    hdrProbe.colorPrimaries.contains("bt2020"),
                )
                assertTrue(
                    "Expected smpte2084 transfer on HDR input, got ${hdrProbe.colorTransfer}",
                    hdrProbe.colorTransfer.contains("smpte2084"),
                )

                val pipeline =
                    VideoProcessingPipeline(
                        dispatchers = DefaultDispatcherProvider,
                        executor = FFmpegKitCommandExecutor(context),
                        stagingCleaner = StagingDirectoryCleaner(context),
                    )

                runHdrCase(
                    pipeline = pipeline,
                    inputFile = hdrInput,
                    encoder = VideoEncoder.CPU_LIBX264,
                    aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                    platform = TargetPlatform.WA_STORY,
                    label = "HDR->SDR CPU_LIBX264 BLUR_BG_9_16",
                    report = report,
                )
                runHdrCase(
                    pipeline = pipeline,
                    inputFile = hdrInput,
                    encoder = VideoEncoder.GPU_MEDIACODEC,
                    aspectMode = CanvasAspectMode.CROP_9_16,
                    platform = TargetPlatform.IG_STORY,
                    label = "HDR->SDR GPU_MEDIACODEC CROP_9_16",
                    report = report,
                )

                val longInput = File(workDir, "input_65s_30fps.mp4")
                generateLongInput65s(longInput)
                runSplitCase(
                    pipeline = pipeline,
                    inputFile = longInput,
                    platform = TargetPlatform.WA_STORY,
                    encoder = VideoEncoder.CPU_LIBX264,
                    splitMode = AutoSplitMode.EVERY_30S,
                    expectedParts = 3,
                    label = "Auto-Split 30s WA_STORY (CPU_LIBX264)",
                    report = report,
                )
                runSplitCase(
                    pipeline = pipeline,
                    inputFile = longInput,
                    platform = TargetPlatform.IG_STORY,
                    encoder = VideoEncoder.GPU_MEDIACODEC,
                    splitMode = AutoSplitMode.EVERY_60S,
                    expectedParts = 2,
                    label = "Auto-Split 60s IG_STORY (GPU_MEDIACODEC)",
                    report = report,
                )
            } finally {
                workDir.deleteRecursively()
                StagingDirectoryCleaner(context).deleteOrphanedOlderThan(maxAgeMs = 0L)
            }

            saveTextEvidence("t32_video_hdr_encoders_split_report.txt", report.toString())
        }

    @Test
    fun t33_foregroundServiceBackgroundProgressCancelAndStagingAudit() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            }

            val workDir = File(checkNotNull(context.externalCacheDir), "phase3_fgs_work").apply { mkdirs() }
            val stagingRoot = File(context.cacheDir, "zidee_staging")
            stagingRoot.deleteRecursively()
            stagingRoot.mkdirs()

            val report = StringBuilder()
            report.appendLine(
                "=== T-3.3 Foreground Service, Background Cancel & Staging Audit " +
                    "(${Build.MODEL}, Android ${Build.VERSION.RELEASE}) ===",
            )

            val engine =
                OfflineMediaOptimizerEngine(
                    context = context,
                    photoPipeline = PhotoProcessingPipeline(context, DefaultDispatcherProvider),
                    videoPipeline =
                        VideoProcessingPipeline(
                            dispatchers = DefaultDispatcherProvider,
                            executor = FFmpegKitCommandExecutor(context),
                            stagingCleaner = StagingDirectoryCleaner(context),
                        ),
                )

            ActivityScenario.launch(MainActivity::class.java).use {
                try {
                    val shortVideo = File(workDir, "short_3s.mp4")
                    generateSimpleSdrVideo(shortVideo, width = 640, height = 360, durationSec = 3)

                    auditSuccessSaveFlow(engine, shortVideo, stagingRoot, report)
                    auditFailureFlow(engine, workDir, stagingRoot, report)

                    val cancelVideo = File(workDir, "cancel_target_20s.mp4")
                    generateSimpleSdrVideo(cancelVideo, width = 1_280, height = 720, durationSec = 20)
                    auditBackgroundCancelFlow(engine, cancelVideo, stagingRoot, report)
                } finally {
                    workDir.deleteRecursively()
                    StagingDirectoryCleaner(context).deleteOrphanedOlderThan(maxAgeMs = 0L)
                }
            }

            saveTextEvidence("t33_fgs_cancel_staging_audit.txt", report.toString())
        }

    @Suppress("LongParameterList")
    private suspend fun runHdrCase(
        pipeline: VideoProcessingPipeline,
        inputFile: File,
        encoder: VideoEncoder,
        aspectMode: CanvasAspectMode,
        platform: TargetPlatform,
        label: String,
        report: StringBuilder,
    ) {
        val uri = MediaUri(Uri.fromFile(inputFile).toString())
        val spec =
            calculate(
                OptimizationRequest(
                    source =
                        MediaMetadata(
                            uri = uri,
                            mediaType = MediaType.VIDEO,
                            width = 1_920,
                            height = 1_080,
                            sizeBytes = inputFile.length(),
                            durationMs = 3_000L,
                            frameRate = 60.0,
                            isHdr = true,
                        ),
                    platform = platform,
                    aspectMode = aspectMode,
                    videoConfig =
                        VideoAdvancedConfig(
                            encoder = encoder,
                            frameRateMode = FrameRateMode.LOCK_30,
                            hdrToSdr = true,
                            autoSplit = AutoSplitMode.OFF,
                        ),
                ),
            )

        val events = pipeline.process(uri, spec).toList()
        val runningFractions = events.filterIsInstance<EngineProgress.Running>().map { it.fraction }
        val completed = events.filterIsInstance<EngineProgress.Completed>().single()
        val outFile = File(completed.outputs.single().cachePath)

        try {
            val probe = probeVideo(outFile)
            val mmr = inspectWithMediaMetadataRetriever(outFile)
            report.appendLine("[$label]")
            report.appendLine("  progressUpdates=${runningFractions.size} (${runningFractions.first()}..${runningFractions.last()})")
            report.appendLine("  FFprobe: $probe")
            report.appendLine("  MediaMetadataRetriever: $mmr")

            assertEquals(1_080, probe.width)
            assertEquals(1_920, probe.height)
            assertTrue("Expected ~30fps, got ${probe.fps}", probe.fps in 29.0..31.0)
            assertEquals("yuv420p", probe.pixFmt)
            assertEquals("bt709", probe.colorPrimaries)
            assertEquals("bt709", probe.colorTransfer)
            assertEquals("bt709", probe.colorSpace)
        } finally {
            outFile.parentFile?.deleteRecursively()
        }
    }

    @Suppress("LongParameterList")
    private suspend fun runSplitCase(
        pipeline: VideoProcessingPipeline,
        inputFile: File,
        platform: TargetPlatform,
        encoder: VideoEncoder,
        splitMode: AutoSplitMode,
        expectedParts: Int,
        label: String,
        report: StringBuilder,
    ) {
        val uri = MediaUri(Uri.fromFile(inputFile).toString())
        val spec =
            calculate(
                OptimizationRequest(
                    source =
                        MediaMetadata(
                            uri = uri,
                            mediaType = MediaType.VIDEO,
                            width = 640,
                            height = 360,
                            sizeBytes = inputFile.length(),
                            durationMs = 65_000L,
                            frameRate = 30.0,
                        ),
                    platform = platform,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                    videoConfig =
                        VideoAdvancedConfig(
                            encoder = encoder,
                            frameRateMode = FrameRateMode.LOCK_30,
                            autoSplit = splitMode,
                            antiBlurSharpening = false,
                        ),
                ),
            )

        val events = pipeline.process(uri, spec).toList()
        val outputs = events.filterIsInstance<EngineProgress.Completed>().single().outputs
        report.appendLine("[$label] parts=${outputs.size} (expected=$expectedParts)")
        try {
            assertEquals("Unexpected segment count for $label", expectedParts, outputs.size)
            for (part in outputs) {
                val partFile = File(part.cachePath)
                val probe = probeVideo(partFile)
                report.appendLine(
                    "  part ${part.partIndex}/${part.totalParts}: ${partFile.name}, bytes=${part.sizeBytes}, $probe",
                )
                assertTrue("Part ${part.partIndex} empty", partFile.length() > 0L)
                assertEquals(640, probe.width)
                assertEquals(360, probe.height)
                assertTrue("Expected ~30fps, got ${probe.fps}", probe.fps in 29.0..31.0)
            }
        } finally {
            outputs.firstOrNull()?.let { File(it.cachePath).parentFile?.deleteRecursively() }
        }
    }

    private suspend fun auditSuccessSaveFlow(
        engine: OfflineMediaOptimizerEngine,
        inputFile: File,
        stagingRoot: File,
        report: StringBuilder,
    ) {
        val before = listStagingEntries(stagingRoot)
        val saver = ZideeMediaStoreSaver(context, DefaultDispatcherProvider)
        val noOpHistory =
            object : OptimizationHistoryRepository {
                override fun observeHistory(): Flow<List<OptimizedMediaItem>> = emptyFlow()

                override suspend fun record(items: List<OptimizedMediaItem>): AppResult<Unit> = AppResult.Success(Unit)

                override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
            }
        val useCase = OptimizeMediaUseCase(calculate, engine, saver, noOpHistory)
        val uri = MediaUri(Uri.fromFile(inputFile).toString())
        val statuses =
            useCase(
                OptimizationRequest(
                    source =
                        MediaMetadata(
                            uri = uri,
                            mediaType = MediaType.VIDEO,
                            width = 640,
                            height = 360,
                            sizeBytes = inputFile.length(),
                            durationMs = 3_000L,
                            frameRate = 30.0,
                        ),
                    platform = TargetPlatform.WA_CHAT,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                    videoConfig = VideoAdvancedConfig(encoder = VideoEncoder.CPU_LIBX264, autoSplit = AutoSplitMode.OFF),
                ),
            ).toList()

        val completed = statuses.filterIsInstance<OptimizationStatus.Completed>().single()
        val savedItem = completed.items.single()
        val savedUri = Uri.parse(savedItem.mediaStoreUri.value)
        val existsInGallery = saver.exists(savedItem.mediaStoreUri)
        val after = listStagingEntries(stagingRoot)

        context.contentResolver.delete(savedUri, null, null)

        report.appendLine("[Staging Audit 1 - Success & Saved]")
        report.appendLine("  before=$before")
        report.appendLine("  savedUri=${savedItem.mediaStoreUri.value}, existsInMediaStore=$existsInGallery")
        report.appendLine("  after=$after")

        assertTrue("Saved video must exist in MediaStore", existsInGallery)
        assertTrue("Staging dir must be clean before job: $before", before.isEmpty())
        assertTrue("Staging dir must be clean after save: $after", after.isEmpty())
    }

    private suspend fun auditFailureFlow(
        engine: OfflineMediaOptimizerEngine,
        workDir: File,
        stagingRoot: File,
        report: StringBuilder,
    ) {
        val corruptFile = File(workDir, "corrupt.mp4").apply { writeText("NOT_A_VALID_MP4_STREAM") }
        val before = listStagingEntries(stagingRoot)
        val uri = MediaUri(Uri.fromFile(corruptFile).toString())
        val spec =
            calculate(
                OptimizationRequest(
                    source =
                        MediaMetadata(
                            uri = uri,
                            mediaType = MediaType.VIDEO,
                            width = 640,
                            height = 360,
                            sizeBytes = corruptFile.length(),
                            durationMs = 5_000L,
                            frameRate = 30.0,
                        ),
                    platform = TargetPlatform.WA_CHAT,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                ),
            )

        var caughtError: Throwable? = null
        try {
            engine.render(uri, spec).toList()
        } catch (t: Throwable) {
            caughtError = t
        }
        val after = listStagingEntries(stagingRoot)

        report.appendLine("[Staging Audit 2 - Failure]")
        report.appendLine("  before=$before")
        report.appendLine("  error=${caughtError?.javaClass?.simpleName}: ${caughtError?.message?.take(120)}")
        report.appendLine("  after=$after")

        assertNotNull("Corrupt video must fail processing", caughtError)
        assertTrue("Staging dir must be clean after failure: $after", after.isEmpty())
    }

    private suspend fun auditBackgroundCancelFlow(
        engine: OfflineMediaOptimizerEngine,
        inputFile: File,
        stagingRoot: File,
        report: StringBuilder,
    ) {
        val before = listStagingEntries(stagingRoot)
        val uri = MediaUri(Uri.fromFile(inputFile).toString())
        val spec =
            calculate(
                OptimizationRequest(
                    source =
                        MediaMetadata(
                            uri = uri,
                            mediaType = MediaType.VIDEO,
                            width = 1_280,
                            height = 720,
                            sizeBytes = inputFile.length(),
                            durationMs = 20_000L,
                            frameRate = 30.0,
                        ),
                    platform = TargetPlatform.WA_STORY,
                    aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                    videoConfig = VideoAdvancedConfig(encoder = VideoEncoder.CPU_LIBX264, autoSplit = AutoSplitMode.OFF),
                ),
            )

        val notificationManager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        val observedNotifications = mutableListOf<String>()
        val observedFlowPercents = mutableListOf<Int>()
        var cancelledViaException = false

        val renderJob =
            kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                try {
                    engine.render(uri, spec).collect { progress ->
                        if (progress is EngineProgress.Running) {
                            observedFlowPercents += (progress.fraction * 100f).toInt()
                        }
                    }
                } catch (_: CancellationException) {
                    cancelledViaException = true
                }
            }

        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()

        var cancelSent = false
        var cancelActionTitle: String? = null
        val deadline = System.currentTimeMillis() + 20_000L
        while (System.currentTimeMillis() < deadline && renderJob.isActive) {
            val active =
                notificationManager.activeNotifications.firstOrNull {
                    it.id == MediaOptimizationForegroundService.NOTIFICATION_ID
                }
            if (active != null) {
                val extras = active.notification.extras
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                val prog = extras.getInt(Notification.EXTRA_PROGRESS, -1)
                val snapshot = "text='$text' progress=$prog"
                if (observedNotifications.lastOrNull() != snapshot) {
                    observedNotifications += snapshot
                }
                val cancelAction = active.notification.actions?.firstOrNull()
                if (!cancelSent && cancelAction != null && (prog > 0 || observedNotifications.size >= 2)) {
                    cancelActionTitle = cancelAction.title?.toString()
                    cancelAction.actionIntent.send()
                    cancelSent = true
                }
            }
            delay(150)
        }
        renderJob.join()

        val afterImmediate = listStagingEntries(stagingRoot)
        delay(1_000)
        val afterSettled = listStagingEntries(stagingRoot)
        val notificationStillActive =
            notificationManager.activeNotifications.any {
                it.id == MediaOptimizationForegroundService.NOTIFICATION_ID
            }

        report.appendLine("[Staging Audit 3 - Background Progress & Notification Cancel]")
        report.appendLine("  before=$before")
        report.appendLine("  observedFlowPercents=${observedFlowPercents.distinct().take(12)}")
        report.appendLine("  observedBackgroundNotifications=$observedNotifications")
        report.appendLine("  cancelActionTitle=$cancelActionTitle, cancelSent=$cancelSent, cancelledViaException=$cancelledViaException")
        report.appendLine("  notificationStillActiveAfterCancel=$notificationStillActive")
        report.appendLine("  afterImmediate=$afterImmediate")
        report.appendLine("  afterSettled1000ms=$afterSettled")

        assertTrue("Expected active foreground notification updates while backgrounded", observedNotifications.isNotEmpty())
        assertTrue("Expected Cancel action to be triggered from active notification", cancelSent)
        assertTrue("Expected render job to terminate via CancellationException", cancelledViaException)
        assertFalse("Notification 1001 must be removed after cancel", notificationStillActive)
        assertTrue("Staging dir must be clean immediately after cancel: $afterImmediate", afterImmediate.isEmpty())
        assertTrue("Staging dir must remain clean 1s after cancel: $afterSettled", afterSettled.isEmpty())
    }

    private fun generateHdr10Input(outFile: File) {
        val session =
            FFmpegKit.executeWithArguments(
                arrayOf(
                    "-y",
                    "-f", "lavfi", "-i", "testsrc2=s=1920x1080:r=60:d=3",
                    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000:duration=3",
                    "-c:v", "libx265",
                    "-preset", "ultrafast",
                    "-pix_fmt", "yuv420p10le",
                    "-color_primaries", "bt2020",
                    "-color_trc", "smpte2084",
                    "-colorspace", "bt2020nc",
                    "-x265-params", "hdr-opt=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc",
                    "-c:a", "aac", "-b:a", "128k",
                    "-shortest",
                    outFile.absolutePath,
                ),
            )
        assertTrue(
            "Failed generating HDR10 test video: ${session.allLogsAsString}",
            ReturnCode.isSuccess(session.returnCode) && outFile.length() > 0L,
        )
    }

    private fun generateLongInput65s(outFile: File) {
        generateSimpleSdrVideo(outFile, width = 640, height = 360, durationSec = 65)
    }

    private fun generateSimpleSdrVideo(
        outFile: File,
        width: Int,
        height: Int,
        durationSec: Int,
    ) {
        val session =
            FFmpegKit.executeWithArguments(
                arrayOf(
                    "-y",
                    "-f", "lavfi", "-i", "testsrc2=s=${width}x$height:r=30:d=$durationSec",
                    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=$durationSec",
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "28", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "96k",
                    "-shortest",
                    outFile.absolutePath,
                ),
            )
        assertTrue(
            "Failed generating test video ${outFile.name}: ${session.allLogsAsString}",
            ReturnCode.isSuccess(session.returnCode) && outFile.length() > 0L,
        )
    }

    private data class VideoProbeResult(
        val width: Int,
        val height: Int,
        val fps: Double,
        val durationSec: Double,
        val codec: String,
        val pixFmt: String,
        val colorPrimaries: String,
        val colorTransfer: String,
        val colorSpace: String,
    ) {
        override fun toString(): String =
            String.format(
                Locale.US,
                "%dx%d @ %.2ffps, dur=%.2fs, codec=%s, pix_fmt=%s, primaries=%s, trc=%s, space=%s",
                width,
                height,
                fps,
                durationSec,
                codec,
                pixFmt,
                colorPrimaries,
                colorTransfer,
                colorSpace,
            )
    }

    private fun probeVideo(file: File): VideoProbeResult {
        val session = FFprobeKit.getMediaInformation(file.absolutePath)
        val info = checkNotNull(session.mediaInformation) { "FFprobe failed for ${file.name}: ${session.allLogsAsString}" }
        val videoStream = checkNotNull(info.streams.firstOrNull { it.type == "video" }) { "No video stream in ${file.name}" }
        val props = videoStream.allProperties
        return VideoProbeResult(
            width = videoStream.width?.toInt() ?: 0,
            height = videoStream.height?.toInt() ?: 0,
            fps = parseFrameRate(videoStream.averageFrameRate ?: videoStream.realFrameRate),
            durationSec = info.duration?.toDoubleOrNull() ?: 0.0,
            codec = videoStream.codec.orEmpty(),
            pixFmt = props?.optString("pix_fmt").orEmpty(),
            colorPrimaries = props?.optString("color_primaries").orEmpty(),
            colorTransfer = props?.optString("color_transfer").orEmpty(),
            colorSpace = props?.optString("color_space").orEmpty(),
        )
    }

    private fun inspectWithMediaMetadataRetriever(file: File): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val colorStd = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)
            val colorTrc = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
            "w=$w, h=$h, rot=$rot, durMs=$dur, colorStd=$colorStd, colorTrc=$colorTrc"
        } finally {
            retriever.release()
        }
    }

    private fun parseFrameRate(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        val parts = raw.split('/')
        return if (parts.size == 2) {
            val num = parts[0].toDoubleOrNull() ?: 0.0
            val den = parts[1].toDoubleOrNull() ?: 1.0
            if (den > 0.0) num / den else 0.0
        } else {
            raw.toDoubleOrNull() ?: 0.0
        }
    }

    private fun listStagingEntries(root: File): List<String> =
        root.walkTopDown()
            .filter { it != root }
            .map { it.relativeTo(root).path }
            .sorted()
            .toList()

    private fun saveTextEvidence(
        fileName: String,
        content: String,
    ) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase3_evidence")
            }
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }
}
