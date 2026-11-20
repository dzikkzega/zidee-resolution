package com.zidee.ress

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.data.mediastore.AndroidMediaStoreGateway
import com.zidee.ress.data.mediastore.MediaStoreGateway
import com.zidee.ress.data.mediastore.ZideeMediaStoreSaver
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.MediaMetadataRepository
import com.zidee.ress.domain.repository.MediaOptimizerEngine
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.repository.UserPreferencesRepository
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import com.zidee.ress.engine.StagingDirectoryCleaner
import com.zidee.ress.presentation.quickshare.QuickShareUiEffect
import com.zidee.ress.presentation.quickshare.QuickShareUiEvent
import com.zidee.ress.presentation.quickshare.QuickShareViewModel
import com.zidee.ress.presentation.share.DirectShareTarget
import com.zidee.ress.presentation.studio.StudioEditorUiEffect
import com.zidee.ress.presentation.studio.StudioEditorUiEvent
import com.zidee.ress.presentation.studio.StudioEditorViewModel
import com.zidee.ress.presentation.studio.StudioProcessingState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class Phase4GalleryAcceptanceTest : KoinComponent {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun p4_studioAndQuickShare_photoAndVideo_publishToGalleryAndSaveBeforeShare() =
        runBlocking {
            val workDir = File(checkNotNull(context.externalCacheDir), "phase4_gallery_work").apply { mkdirs() }
            val tempInputUris = mutableListOf<Uri>()
            val report = StringBuilder()
            report.appendLine("=== Phase 4 Gallery DoD: Studio & Quick Share on ${Build.MODEL} (API ${Build.VERSION.SDK_INT}) ===")

            val historyRepo: OptimizationHistoryRepository = get()
            val prefsRepo: UserPreferencesRepository = get()
            val metadataRepo: MediaMetadataRepository = get()
            val optimizeUseCase: OptimizeMediaUseCase = get()
            val calcUseCase: CalculateOptimalSpecUseCase = get()

            try {
                val photoFile = createPhotoFixture(File(workDir, "p4_input_photo.jpg"))
                val videoFile = createVideoFixture(File(workDir, "p4_input_video.mp4"))
                val photoContentUri = insertInputIntoMediaStore(context, photoFile, MediaType.PHOTO).also { tempInputUris += it }
                val videoContentUri = insertInputIntoMediaStore(context, videoFile, MediaType.VIDEO).also { tempInputUris += it }

                val photoMediaUri = MediaUri(photoContentUri.toString())
                val videoMediaUri = MediaUri(videoContentUri.toString())

                val photoInspect = metadataRepo.inspect(photoMediaUri)
                assertTrue("Photo inspect failed for $photoContentUri: $photoInspect", photoInspect is AppResult.Success)
                val videoInspect = metadataRepo.inspect(videoMediaUri)
                assertTrue("Video inspect failed for $videoContentUri: $videoInspect", videoInspect is AppResult.Success)

                runStudioPhotoFlow(photoMediaUri, calcUseCase, optimizeUseCase, metadataRepo, prefsRepo, historyRepo, report)
                runStudioVideoShareFlow(videoMediaUri, calcUseCase, optimizeUseCase, metadataRepo, prefsRepo, historyRepo, report)
                runQuickSharePhotoShareFlow(photoMediaUri, calcUseCase, optimizeUseCase, metadataRepo, prefsRepo, historyRepo, report)
                runQuickShareVideoSaveFlow(videoMediaUri, calcUseCase, optimizeUseCase, metadataRepo, prefsRepo, historyRepo, report)
                runZeroRowPublishRegressionOnDevice(photoMediaUri, calcUseCase, metadataRepo, prefsRepo, historyRepo, report)
            } finally {
                tempInputUris.forEach { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
                workDir.deleteRecursively()
                StagingDirectoryCleaner(context).deleteOrphanedOlderThan(maxAgeMs = 0L)
            }

            savePhase4Evidence(context, "p4_gallery_studio_quickshare_report.txt", report.toString())
        }

    private suspend fun runStudioPhotoFlow(
        sourceUri: MediaUri,
        calc: CalculateOptimalSpecUseCase,
        optimize: OptimizeMediaUseCase,
        metadata: MediaMetadataRepository,
        prefs: UserPreferencesRepository,
        history: OptimizationHistoryRepository,
        report: StringBuilder,
    ) = coroutineScope {
        val vm = StudioEditorViewModel(calc, optimize, metadata, prefs)
        val savedEffect = CompletableDeferred<StudioEditorUiEffect.SavedSuccessfully>()
        val collector =
            launch {
                vm.effect.collect { effect ->
                    if (effect is StudioEditorUiEffect.SavedSuccessfully && !savedEffect.isCompleted) {
                        savedEffect.complete(effect)
                    }
                }
            }
        try {
            vm.onEvent(StudioEditorUiEvent.LoadSources(listOf(sourceUri), TargetPlatform.WA_STORY))
            val ready =
                withTimeout(15_000L) {
                    vm.state.first { it.canExport || (it.queue.isNotEmpty() && it.queue.all { e -> !e.isLoading }) }
                }
            assertTrue("Studio photo load failed: state=$ready", ready.canExport)
            vm.onEvent(StudioEditorUiEvent.SelectAspectMode(CanvasAspectMode.CROP_9_16))
            vm.onEvent(StudioEditorUiEvent.SaveToGallery)

            val done = withTimeout(60_000L) { savedEffect.await() }
            assertEquals(1, done.items.size)
            val item = done.items.first()
            val row = verifyGalleryItem(item, MediaType.PHOTO, history)
            report.appendLine("[Studio -> Photo SaveToGallery]")
            report.appendLine("  uri=${item.mediaStoreUri.value}")
            report.appendLine(
                "  dir=${row.relativePath}, bucket=${row.bucketDisplayName}, " +
                    "isPending=${row.isPending}, bytes=${row.sizeBytes}",
            )
            report.appendLine("  visibleInAlbumQuery=${row.visibleInAlbumQuery}, recordedInRoom=true")
        } finally {
            collector.cancel()
        }
    }

    private suspend fun runStudioVideoShareFlow(
        sourceUri: MediaUri,
        calc: CalculateOptimalSpecUseCase,
        optimize: OptimizeMediaUseCase,
        metadata: MediaMetadataRepository,
        prefs: UserPreferencesRepository,
        history: OptimizationHistoryRepository,
        report: StringBuilder,
    ) = coroutineScope {
        val vm = StudioEditorViewModel(calc, optimize, metadata, prefs)
        val shareObserved = CompletableDeferred<ShareOrderProof>()
        val collector =
            launch {
                vm.effect.collect { effect ->
                    if (effect is StudioEditorUiEffect.LaunchShare && !shareObserved.isCompleted) {
                        val sharedUri = effect.payload.uris.first()
                        val rowAtShareTime = queryMediaRow(Uri.parse(sharedUri.value), MediaType.VIDEO)
                        val historyAtShareTime = history.observeHistory().first()
                        shareObserved.complete(
                            ShareOrderProof(
                                sharedUri = sharedUri.value,
                                rowAtShareTime = rowAtShareTime,
                                historyHasUriAtShareTime = historyAtShareTime.any { it.mediaStoreUri == sharedUri },
                            ),
                        )
                    }
                }
            }
        try {
            vm.onEvent(StudioEditorUiEvent.LoadSources(listOf(sourceUri), TargetPlatform.IG_STORY))
            val ready =
                withTimeout(15_000L) {
                    vm.state.first { it.canExport || (it.queue.isNotEmpty() && it.queue.all { e -> !e.isLoading }) }
                }
            assertTrue("Studio video load failed: state=$ready", ready.canExport)
            vm.onEvent(StudioEditorUiEvent.SelectAspectMode(CanvasAspectMode.CROP_9_16))
            vm.onEvent(StudioEditorUiEvent.SaveAndShare(DirectShareTarget.INSTAGRAM))

            val proof = withTimeout(90_000L) { shareObserved.await() }
            val completed = vm.state.value.processingState as? StudioProcessingState.Completed
            assertNotNull("Expected Studio Completed state", completed)
            assertEquals("Movies/ZideeResolution/", proof.rowAtShareTime.relativePath)
            assertEquals("ZideeResolution", proof.rowAtShareTime.bucketDisplayName)
            assertEquals(0, proof.rowAtShareTime.isPending)
            assertTrue("Video must be visible in album before share intent", proof.rowAtShareTime.visibleInAlbumQuery)
            assertTrue("Room history must be recorded before share intent", proof.historyHasUriAtShareTime)
            report.appendLine("[Studio -> Video SaveAndShare]")
            report.appendLine("  uri=${proof.sharedUri}")
            report.appendLine(
                "  atShareIntentTime: dir=${proof.rowAtShareTime.relativePath}, bucket=${proof.rowAtShareTime.bucketDisplayName}, " +
                    "isPending=${proof.rowAtShareTime.isPending}, bytes=${proof.rowAtShareTime.sizeBytes}, " +
                    "visibleInAlbumQuery=${proof.rowAtShareTime.visibleInAlbumQuery}, " +
                    "historyRecordedBeforeShare=${proof.historyHasUriAtShareTime}",
            )
        } finally {
            collector.cancel()
        }
    }

    private suspend fun runQuickSharePhotoShareFlow(
        sourceUri: MediaUri,
        calc: CalculateOptimalSpecUseCase,
        optimize: OptimizeMediaUseCase,
        metadata: MediaMetadataRepository,
        prefs: UserPreferencesRepository,
        history: OptimizationHistoryRepository,
        report: StringBuilder,
    ) = coroutineScope {
        val vm = QuickShareViewModel(calc, optimize, metadata, prefs)
        val shareObserved = CompletableDeferred<ShareOrderProof>()
        val collector =
            launch {
                vm.effect.collect { effect ->
                    if (effect is QuickShareUiEffect.LaunchTargetAppShare && !shareObserved.isCompleted) {
                        val sharedUri = effect.payload.uris.first()
                        val rowAtShareTime = queryMediaRow(Uri.parse(sharedUri.value), MediaType.PHOTO)
                        val historyAtShareTime = history.observeHistory().first()
                        shareObserved.complete(
                            ShareOrderProof(
                                sharedUri = sharedUri.value,
                                rowAtShareTime = rowAtShareTime,
                                historyHasUriAtShareTime = historyAtShareTime.any { it.mediaStoreUri == sharedUri },
                            ),
                        )
                    }
                }
            }
        try {
            vm.onEvent(QuickShareUiEvent.LoadSources(listOf(sourceUri)))
            val ready = withTimeout(15_000L) { vm.state.first { it.canSubmit || (!it.isInspecting && it.errorMessage != null) } }
            assertTrue("QuickShare photo load failed: state=$ready", ready.canSubmit)
            vm.onEvent(QuickShareUiEvent.SelectPlatform(TargetPlatform.WA_CHAT))
            vm.onEvent(QuickShareUiEvent.ClickSaveAndShare(DirectShareTarget.WHATSAPP))

            val proof = withTimeout(60_000L) { shareObserved.await() }
            assertEquals("Pictures/ZideeResolution/", proof.rowAtShareTime.relativePath)
            assertEquals("ZideeResolution", proof.rowAtShareTime.bucketDisplayName)
            assertEquals(0, proof.rowAtShareTime.isPending)
            assertTrue("Photo must be visible in album before share intent", proof.rowAtShareTime.visibleInAlbumQuery)
            assertTrue("Room history must be recorded before share intent", proof.historyHasUriAtShareTime)
            report.appendLine("[QuickShare -> Photo ClickSaveAndShare]")
            report.appendLine("  uri=${proof.sharedUri}")
            report.appendLine(
                "  atShareIntentTime: dir=${proof.rowAtShareTime.relativePath}, bucket=${proof.rowAtShareTime.bucketDisplayName}, " +
                    "isPending=${proof.rowAtShareTime.isPending}, bytes=${proof.rowAtShareTime.sizeBytes}, " +
                    "visibleInAlbumQuery=${proof.rowAtShareTime.visibleInAlbumQuery}, " +
                    "historyRecordedBeforeShare=${proof.historyHasUriAtShareTime}",
            )
        } finally {
            collector.cancel()
        }
    }

    private suspend fun runQuickShareVideoSaveFlow(
        sourceUri: MediaUri,
        calc: CalculateOptimalSpecUseCase,
        optimize: OptimizeMediaUseCase,
        metadata: MediaMetadataRepository,
        prefs: UserPreferencesRepository,
        history: OptimizationHistoryRepository,
        report: StringBuilder,
    ) = coroutineScope {
        val vm = QuickShareViewModel(calc, optimize, metadata, prefs)
        val savedDeferred = CompletableDeferred<QuickShareUiEffect.SavedOnlyCompleted>()
        val collector =
            launch {
                vm.effect.collect { effect ->
                    if (effect is QuickShareUiEffect.SavedOnlyCompleted && !savedDeferred.isCompleted) {
                        savedDeferred.complete(effect)
                    }
                }
            }
        try {
            vm.onEvent(QuickShareUiEvent.LoadSources(listOf(sourceUri)))
            val ready = withTimeout(15_000L) { vm.state.first { it.canSubmit || (!it.isInspecting && it.errorMessage != null) } }
            assertTrue("QuickShare video load failed: state=$ready", ready.canSubmit)
            vm.onEvent(QuickShareUiEvent.SelectPlatform(TargetPlatform.WA_STORY))
            vm.onEvent(QuickShareUiEvent.ClickSaveOnly)

            val done = withTimeout(90_000L) { savedDeferred.await() }
            assertEquals(1, done.items.size)
            val item = done.items.first()
            val row = verifyGalleryItem(item, MediaType.VIDEO, history)
            report.appendLine("[QuickShare -> Video ClickSaveOnly]")
            report.appendLine("  uri=${item.mediaStoreUri.value}")
            report.appendLine(
                "  dir=${row.relativePath}, bucket=${row.bucketDisplayName}, " +
                    "isPending=${row.isPending}, bytes=${row.sizeBytes}",
            )
            report.appendLine("  visibleInAlbumQuery=${row.visibleInAlbumQuery}, recordedInRoom=true")
        } finally {
            collector.cancel()
        }
    }

    private suspend fun runZeroRowPublishRegressionOnDevice(
        sourceUri: MediaUri,
        calc: CalculateOptimalSpecUseCase,
        metadata: MediaMetadataRepository,
        prefs: UserPreferencesRepository,
        history: OptimizationHistoryRepository,
        report: StringBuilder,
    ) = coroutineScope {
        val realGateway = AndroidMediaStoreGateway(context)
        var pendingUriCaptured: String? = null
        val zeroRowGateway =
            object : MediaStoreGateway by realGateway {
                override fun markCommitted(
                    uri: String,
                    displayName: String,
                    mediaType: MediaType,
                ): Int {
                    pendingUriCaptured = uri
                    return 0
                }
            }
        val dispatchers: DispatcherProvider = get()
        val engine: MediaOptimizerEngine = get()
        val zeroRowSaver = ZideeMediaStoreSaver(zeroRowGateway, dispatchers)
        val zeroRowOptimize = OptimizeMediaUseCase(calc, engine, zeroRowSaver, history)

        val historyCountBefore = history.observeHistory().first().size
        val vm = QuickShareViewModel(calc, zeroRowOptimize, metadata, prefs)
        val effects = mutableListOf<QuickShareUiEffect>()
        val collector = launch { vm.effect.collect { effects += it } }
        try {
            vm.onEvent(QuickShareUiEvent.LoadSources(listOf(sourceUri)))
            val ready = withTimeout(15_000L) { vm.state.first { it.canSubmit || (!it.isInspecting && it.errorMessage != null) } }
            assertTrue("Zero-row regression load failed: state=$ready", ready.canSubmit)
            vm.onEvent(QuickShareUiEvent.ClickSaveAndShare(DirectShareTarget.WHATSAPP))
            withTimeout(60_000L) { vm.state.first { !it.isProcessing && it.errorMessage != null } }

            val historyCountAfter = history.observeHistory().first().size
            val pendingStillExists = pendingUriCaptured?.let(realGateway::exists) ?: false
            val stagingLeft = File(context.cacheDir, "zidee_staging").list().orEmpty().toList()

            assertNotNull(vm.state.value.errorMessage)
            assertEquals("Room history count must not increase on update=0", historyCountBefore, historyCountAfter)
            assertTrue("No share intent may be emitted on update=0", effects.isEmpty())
            assertFalse("Rolled-back pending row must not remain in MediaStore", pendingStillExists)
            assertTrue("Staging directory must be empty after update=0 failure", stagingLeft.isEmpty())

            report.appendLine("[Regression -> IS_PENDING=0 Update Returns 0 Rows (On-Device)]")
            report.appendLine(
                "  errorReported=true, historyDelta=${historyCountAfter - historyCountBefore}, " +
                    "emittedEffects=${effects.size}, pendingStillExists=$pendingStillExists, stagingLeft=$stagingLeft",
            )
        } finally {
            collector.cancel()
        }
    }

    private suspend fun verifyGalleryItem(
        item: OptimizedMediaItem,
        mediaType: MediaType,
        history: OptimizationHistoryRepository,
    ): GalleryRowProof {
        val row = queryMediaRow(Uri.parse(item.mediaStoreUri.value), mediaType)
        val expectedDir =
            when (mediaType) {
                MediaType.PHOTO -> "Pictures/ZideeResolution/"
                MediaType.VIDEO -> "Movies/ZideeResolution/"
            }
        assertEquals(expectedDir, row.relativePath)
        assertEquals("ZideeResolution", row.bucketDisplayName)
        assertEquals(0, row.isPending)
        assertTrue("Saved file size must be > 0", row.sizeBytes > 0L)
        assertTrue("Saved item must appear in MediaStore album query", row.visibleInAlbumQuery)
        val historyRows = history.observeHistory().first()
        assertTrue("Saved item must exist in Room history", historyRows.any { it.id == item.id })
        return row
    }

    private fun queryMediaRow(
        uri: Uri,
        mediaType: MediaType,
    ): GalleryRowProof {
        val projection =
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.IS_PENDING,
                MediaStore.MediaColumns.SIZE,
            )
        val row =
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                assertTrue("MediaStore row missing for $uri", c.moveToFirst())
                GalleryRowProof(
                    id = c.getLong(0),
                    displayName = c.getString(1).orEmpty(),
                    relativePath = c.getString(2).orEmpty(),
                    bucketDisplayName = c.getString(3).orEmpty(),
                    isPending = c.getInt(4),
                    sizeBytes = c.getLong(5),
                    visibleInAlbumQuery = false,
                )
            } ?: error("Null cursor for $uri")

        val collection =
            when (mediaType) {
                MediaType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        val visibleInAlbum =
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns._ID} = ? AND ${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ?",
                arrayOf(row.id.toString(), "ZideeResolution"),
                null,
            )?.use { it.moveToFirst() } ?: false

        return row.copy(visibleInAlbumQuery = visibleInAlbum)
    }

    private fun createPhotoFixture(target: File): File {
        val bmp = Bitmap.createBitmap(1_200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.color = Color.rgb(18, 92, 168)
        canvas.drawRect(0f, 0f, 1_200f, 450f, paint)
        paint.color = Color.rgb(240, 180, 40)
        canvas.drawRect(0f, 450f, 1_200f, 900f, paint)
        FileOutputStream(target).use { out ->
            check(bmp.compress(Bitmap.CompressFormat.JPEG, 92, out))
        }
        bmp.recycle()
        return target
    }

    private fun createVideoFixture(target: File): File {
        val cmd =
            listOf(
                "-y",
                "-f",
                "lavfi",
                "-i",
                "testsrc2=size=640x360:rate=30:duration=1.5",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:sample_rate=44100:duration=1.5",
                "-c:v",
                "libx264",
                "-pix_fmt",
                "yuv420p",
                "-preset",
                "ultrafast",
                "-c:a",
                "aac",
                "-b:a",
                "96k",
                target.absolutePath,
            ).joinToString(" ")
        val session = FFmpegKit.execute(cmd)
        assertTrue(
            "Failed creating video fixture: ${session.output}",
            ReturnCode.isSuccess(session.returnCode) && target.length() > 0L,
        )
        return target
    }

    private data class GalleryRowProof(
        val id: Long,
        val displayName: String,
        val relativePath: String,
        val bucketDisplayName: String,
        val isPending: Int,
        val sizeBytes: Long,
        val visibleInAlbumQuery: Boolean,
    )

    private data class ShareOrderProof(
        val sharedUri: String,
        val rowAtShareTime: GalleryRowProof,
        val historyHasUriAtShareTime: Boolean,
    )
}

internal fun insertInputIntoMediaStore(
    context: Context,
    file: File,
    mediaType: MediaType,
): Uri {
    val resolver = context.contentResolver
    val collection =
        when (mediaType) {
            MediaType.PHOTO -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
    val values =
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "p4_src_${System.currentTimeMillis()}_${file.name}")
            put(MediaStore.MediaColumns.MIME_TYPE, if (mediaType == MediaType.PHOTO) "image/jpeg" else "video/mp4")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (mediaType == MediaType.PHOTO) "Pictures/ZideeTestInputs" else "Movies/ZideeTestInputs",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    val uri = checkNotNull(resolver.insert(collection, values))
    resolver.openOutputStream(uri, "w")?.use { out ->
        FileInputStream(file).use { input -> input.copyTo(out) }
    }
    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    return uri
}

internal fun savePhase4Evidence(
    context: Context,
    fileName: String,
    content: String,
) {
    val resolver = context.contentResolver
    val values =
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase4_evidence")
        }
    val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
}
