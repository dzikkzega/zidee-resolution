package com.zidee.ress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.repository.MediaMetadataRepository
import com.zidee.ress.domain.repository.MediaStorageRepository
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.repository.UserPreferencesRepository
import com.zidee.ress.domain.usecase.OptimizationStatus
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.io.FileOutputStream

/**
 * Two-step cold-restart persistence verification across an actual `am force-stop com.zidee.ress`.
 * Step 1 writes a real gallery item into Room and updates DataStore preferences.
 * Step 2 runs in a fresh OS process (enforced via `-e requireNewProcess true`) and asserts both
 * Room history and DataStore preferences persist.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase4ColdRestartTest : KoinComponent {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val markerFile: File
        get() = File(context.filesDir, "phase4_cold_restart_marker.txt")

    @Test
    fun step1_writeRoomAndPreferencesBeforeRestart(): Unit =
        runBlocking {
            val workDir = File(checkNotNull(context.externalCacheDir), "phase4_restart_work").apply { mkdirs() }
            val inputFile = File(workDir, "restart_source.jpg")
            val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.rgb(42, 160, 120))
            FileOutputStream(inputFile).use { out -> check(bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) }
            bmp.recycle()

            val inputUri = insertInputIntoMediaStore(context, inputFile, MediaType.PHOTO)
            val metadataRepo: MediaMetadataRepository = get()
            val optimizeUseCase: OptimizeMediaUseCase = get()
            val prefsRepo: UserPreferencesRepository = get()

            try {
                val inspected = metadataRepo.inspect(MediaUri(inputUri.toString()))
                assertTrue("Inspect failed: $inspected", inspected is AppResult.Success)
                val meta = (inspected as AppResult.Success).value

                assertTrue(
                    prefsRepo.setLastSelection(TargetPlatform.IG_STORY, CanvasAspectMode.CROP_9_16) is AppResult.Success,
                )
                assertTrue(prefsRepo.setPreferredEncoder(VideoEncoder.GPU_MEDIACODEC) is AppResult.Success)

                var completedId: String? = null
                var completedUri: String? = null
                var completedName: String? = null
                optimizeUseCase(
                    OptimizationRequest(
                        source = meta,
                        platform = TargetPlatform.IG_STORY,
                        aspectMode = CanvasAspectMode.CROP_9_16,
                    ),
                ).collect { status ->
                    if (status is OptimizationStatus.Completed) {
                        val first = status.items.first()
                        completedId = first.id
                        completedUri = first.mediaStoreUri.value
                        completedName = first.displayName
                    }
                }

                val itemId = checkNotNull(completedId)
                val itemUri = checkNotNull(completedUri)
                val itemName = checkNotNull(completedName)
                val pid = Process.myPid()

                markerFile.writeText(
                    listOf(
                        "pid=$pid",
                        "itemId=$itemId",
                        "itemUri=$itemUri",
                        "itemName=$itemName",
                        "platform=${TargetPlatform.IG_STORY.name}",
                        "aspectMode=${CanvasAspectMode.CROP_9_16.name}",
                        "encoder=${VideoEncoder.GPU_MEDIACODEC.name}",
                    ).joinToString("\n"),
                )
                savePhase4Evidence(
                    context,
                    "p4_cold_restart_before.txt",
                    "=== Phase 4 Cold Restart (Before am force-stop) ===\n${markerFile.readText()}\n",
                )
            } finally {
                runCatching { context.contentResolver.delete(inputUri, null, null) }
                workDir.deleteRecursively()
            }
            Unit
        }

    @Test
    fun step2_verifyRoomAndPreferencesAfterForceStop(): Unit =
        runBlocking {
            assertTrue("Run step1 before step2", markerFile.isFile)
            val marker =
                markerFile.readLines().associate { line ->
                    val idx = line.indexOf('=')
                    line.substring(0, idx) to line.substring(idx + 1)
                }
            val beforePid = checkNotNull(marker["pid"]).toInt()
            val expectedId = checkNotNull(marker["itemId"])
            val expectedUri = checkNotNull(marker["itemUri"])
            val expectedName = checkNotNull(marker["itemName"])
            val afterPid = Process.myPid()
            val requireNewProcess =
                InstrumentationRegistry.getArguments().getString("requireNewProcess") == "true"

            if (requireNewProcess) {
                assertNotEquals("Step 2 must run in a brand new OS process after force-stop", beforePid, afterPid)
            }

            val historyRepo: OptimizationHistoryRepository = get()
            val storageRepo: MediaStorageRepository = get()
            val prefsRepo: UserPreferencesRepository = get()

            val persistedHistory = historyRepo.observeHistory().first()
            val found = persistedHistory.firstOrNull { it.id == expectedId }
            assertNotNull("Expected Room history row $expectedId after cold restart", found)
            val row = checkNotNull(found)
            assertEquals(expectedUri, row.mediaStoreUri.value)
            assertEquals(expectedName, row.displayName)
            assertEquals(MediaType.PHOTO, row.mediaType)
            assertTrue("Persisted gallery file must still exist in MediaStore", storageRepo.exists(row.mediaStoreUri))

            val persistedPrefs = prefsRepo.observePreferences().first()
            assertEquals(TargetPlatform.IG_STORY, persistedPrefs.lastSelectedPlatform)
            assertEquals(CanvasAspectMode.CROP_9_16, persistedPrefs.lastSelectedAspectMode)
            assertEquals(VideoEncoder.GPU_MEDIACODEC, persistedPrefs.preferredVideoEncoder)

            val report =
                buildString {
                    appendLine("=== Phase 4 Cold Restart (After am force-stop & Process Relaunch) ===")
                    appendLine("beforePid=$beforePid, afterPid=$afterPid (newProcess=${beforePid != afterPid})")
                    appendLine("requireNewProcess=$requireNewProcess")
                    appendLine("roomHistoryCount=${persistedHistory.size}")
                    appendLine("roomFoundId=${row.id}, uri=${row.mediaStoreUri.value}, name=${row.displayName}")
                    appendLine("mediaStoreExistsAfterRestart=true")
                    appendLine(
                        "dataStorePrefs: platform=${persistedPrefs.lastSelectedPlatform}, " +
                            "aspectMode=${persistedPrefs.lastSelectedAspectMode}, " +
                            "encoder=${persistedPrefs.preferredVideoEncoder}",
                    )
                }
            savePhase4Evidence(context, "p4_cold_restart_after.txt", report)
            markerFile.delete()
            Unit
        }
}
