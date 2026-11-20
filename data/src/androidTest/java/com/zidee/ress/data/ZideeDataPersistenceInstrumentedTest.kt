package com.zidee.ress.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.data.local.ZideeDatabase
import com.zidee.ress.data.mediastore.AndroidMediaStoreGateway
import com.zidee.ress.data.mediastore.MediaStoreGateway
import com.zidee.ress.data.mediastore.ZideeMediaStoreSaver
import com.zidee.ress.data.preferences.UserPreferencesDataStore
import com.zidee.ress.data.repository.OptimizationHistoryRepositoryImpl
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoEncoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ZideeDataPersistenceInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Main
            override val io: CoroutineDispatcher = Dispatchers.IO
            override val default: CoroutineDispatcher = Dispatchers.Default
        }

    @Test
    fun saveToGallery_publishesPhotoAndVideoToZideeResolutionAlbumWithIsPendingZero() =
        runBlocking {
            val saver = ZideeMediaStoreSaver(context, dispatchers)
            val createdUris = mutableListOf<Uri>()
            try {
                val photoFile = createValidJpegFile("data_test_photo.jpg")
                val photoStaged =
                    StagedOutput(photoFile.absolutePath, MediaType.PHOTO, PixelSize(240, 240), photoFile.length(), null, 1, 1)
                val photoResult = saver.saveToGallery(photoStaged, "ZIDEE_IMG_DATA_TEST_${System.currentTimeMillis()}.jpg")
                assertTrue("Photo save failed: $photoResult", photoResult is AppResult.Success)
                val photoUri = Uri.parse((photoResult as AppResult.Success).value.value)
                createdUris += photoUri

                val photoMeta = queryMediaStoreRow(photoUri)
                assertEquals("Pictures/ZideeResolution/", photoMeta.relativePath)
                assertEquals("ZideeResolution", photoMeta.bucketDisplayName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    assertEquals(0, photoMeta.isPending)
                }
                assertTrue("Photo size must be > 0", photoMeta.sizeBytes > 0L)
                assertFalse("Staged photo file must be deleted", photoFile.exists())
                assertTrue(saver.exists(MediaUri(photoUri.toString())))

                val videoFile = createMinimalMp4File("data_test_video.mp4")
                val videoStaged =
                    StagedOutput(videoFile.absolutePath, MediaType.VIDEO, PixelSize(320, 240), videoFile.length(), 1_000L, 1, 1)
                val videoResult = saver.saveToGallery(videoStaged, "ZIDEE_VID_DATA_TEST_${System.currentTimeMillis()}.mp4")
                assertTrue("Video save failed: $videoResult", videoResult is AppResult.Success)
                val videoUri = Uri.parse((videoResult as AppResult.Success).value.value)
                createdUris += videoUri

                val videoMeta = queryMediaStoreRow(videoUri)
                assertEquals("Movies/ZideeResolution/", videoMeta.relativePath)
                assertEquals("ZideeResolution", videoMeta.bucketDisplayName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    assertEquals(0, videoMeta.isPending)
                }
                assertTrue("Video size must be > 0", videoMeta.sizeBytes > 0L)
                assertFalse("Staged video file must be deleted", videoFile.exists())
                assertTrue(saver.exists(MediaUri(videoUri.toString())))
            } finally {
                createdUris.forEach { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
            }
        }

    @Test
    fun saveToGallery_whenPublishReturnsZeroRows_failsAndDeletesPendingRow() =
        runBlocking {
            val realGateway = AndroidMediaStoreGateway(context)
            var capturedUri: String? = null
            val zeroRowGateway =
                object : MediaStoreGateway by realGateway {
                    override fun markCommitted(
                        uri: String,
                        displayName: String,
                        mediaType: MediaType,
                    ): Int {
                        capturedUri = uri
                        return 0
                    }
                }
            val saver = ZideeMediaStoreSaver(zeroRowGateway, dispatchers)
            val stagedFile = createValidJpegFile("data_zero_rows.jpg")
            val staged = StagedOutput(stagedFile.absolutePath, MediaType.PHOTO, PixelSize(240, 240), stagedFile.length(), null, 1, 1)

            val result = saver.saveToGallery(staged, "ZIDEE_IMG_ZERO_TEST_${System.currentTimeMillis()}.jpg")

            assertTrue("Expected failure on 0 updated rows", result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.StorageWrite)
            assertFalse("Staging file must be deleted", stagedFile.exists())
            val pendingUri = checkNotNull(capturedUri)
            assertFalse("Pending row must be rolled back from MediaStore", realGateway.exists(pendingUri))
        }

    @Test
    fun roomHistory_and_dataStorePreferences_persistAcrossReopen() =
        runBlocking {
            val dbName = "zidee_phase4_data_test.db"
            context.deleteDatabase(dbName)
            try {
                val firstDb = Room.databaseBuilder(context, ZideeDatabase::class.java, dbName).build()
                val firstRepo = OptimizationHistoryRepositoryImpl(firstDb.optimizationHistoryDao(), dispatchers)
                val item =
                    OptimizedMediaItem(
                        id = "phase4-room-item-1",
                        mediaStoreUri = MediaUri("content://media/external_primary/images/media/4242"),
                        displayName = "ZIDEE_IMG_ROOM_PERSIST.jpg",
                        mediaType = MediaType.PHOTO,
                        platform = TargetPlatform.IG_STORY,
                        aspectMode = CanvasAspectMode.CROP_9_16,
                        originalSize = PixelSize(4000, 3000),
                        outputSize = PixelSize(1080, 1920),
                        originalSizeBytes = 3_500_000L,
                        outputSizeBytes = 240_000L,
                        durationMs = null,
                        partIndex = 1,
                        totalParts = 1,
                        createdAtEpochMs = 1_763_500_000_000L,
                    )
                assertTrue(firstRepo.record(listOf(item)) is AppResult.Success)
                firstDb.close()

                val reopenedDb = Room.databaseBuilder(context, ZideeDatabase::class.java, dbName).build()
                try {
                    val reopenedRepo = OptimizationHistoryRepositoryImpl(reopenedDb.optimizationHistoryDao(), dispatchers)
                    val loaded = reopenedRepo.observeHistory().first()
                    assertEquals(1, loaded.size)
                    assertEquals(item, loaded.first())
                } finally {
                    reopenedDb.close()
                }
            } finally {
                context.deleteDatabase(dbName)
            }

            val prefsStore = UserPreferencesDataStore(context)
            assertTrue(prefsStore.setLastSelection(TargetPlatform.IG_STORY, CanvasAspectMode.CROP_9_16) is AppResult.Success)
            assertTrue(prefsStore.setPreferredEncoder(VideoEncoder.GPU_MEDIACODEC) is AppResult.Success)
            val savedPrefs = prefsStore.observePreferences().first()
            assertEquals(TargetPlatform.IG_STORY, savedPrefs.lastSelectedPlatform)
            assertEquals(CanvasAspectMode.CROP_9_16, savedPrefs.lastSelectedAspectMode)
            assertEquals(VideoEncoder.GPU_MEDIACODEC, savedPrefs.preferredVideoEncoder)
        }

    private fun createValidJpegFile(name: String): File {
        val dir = File(context.cacheDir, "job_data_test_${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, name)
        val bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(34, 140, 220))
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bmp.recycle()
        return file
    }

    private fun createMinimalMp4File(name: String): File {
        val dir = File(context.cacheDir, "job_data_test_${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, name)
        // Minimal valid ftyp ISO BMFF header so MediaProvider registers a video entry cleanly.
        val ftyp =
            byteArrayOf(
                0x00, 0x00, 0x00, 0x18,
                'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte(),
                0x00, 0x00, 0x00, 0x00,
                'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte(),
                'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            )
        file.writeBytes(ftyp)
        return file
    }

    private fun queryMediaStoreRow(uri: Uri): MediaStoreRowSnapshot {
        val projection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns.IS_PENDING,
                    MediaStore.MediaColumns.SIZE,
                )
            } else {
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                )
            }
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            assertTrue("Row not found in MediaStore for $uri", cursor.moveToFirst())
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStoreRowSnapshot(
                    displayName = cursor.getString(0).orEmpty(),
                    relativePath = cursor.getString(1).orEmpty(),
                    bucketDisplayName = cursor.getString(2).orEmpty(),
                    isPending = cursor.getInt(3),
                    sizeBytes = cursor.getLong(4),
                )
            } else {
                MediaStoreRowSnapshot(
                    displayName = cursor.getString(0).orEmpty(),
                    relativePath = cursor.getString(1).orEmpty(),
                    bucketDisplayName = cursor.getString(2).orEmpty(),
                    isPending = 0,
                    sizeBytes = cursor.getLong(3),
                )
            }
        }
        error("Null cursor querying $uri")
    }

    private data class MediaStoreRowSnapshot(
        val displayName: String,
        val relativePath: String,
        val bucketDisplayName: String,
        val isPending: Int,
        val sizeBytes: Long,
    )
}
