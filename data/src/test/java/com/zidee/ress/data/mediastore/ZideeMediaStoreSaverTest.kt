package com.zidee.ress.data.mediastore

import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.core.result.AppError
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.repository.MediaOptimizerEngine
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.domain.usecase.OptimizationStatus
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

class ZideeMediaStoreSaverTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `saves photo to Pictures ZideeResolution with IS_PENDING lifecycle and deletes staging file`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway()
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))

            val payload = byteArrayOf(1, 2, 3, 4, 5, 6)
            val stagedFile = tempFolder.newFile("staged.jpg").apply { writeBytes(payload) }
            val staged = StagedOutput(stagedFile.absolutePath, MediaType.PHOTO, PixelSize(1080, 1920), payload.size.toLong(), null, 1, 1)

            val result = saver.saveToGallery(staged, "ZIDEE_IMG_20261118_WA_STORY.jpg")

            assertTrue(result is AppResult.Success)
            val savedUri = (result as AppResult.Success).value
            assertEquals("content://media/external/images/1", savedUri.value)
            assertEquals("Pictures/ZideeResolution", gateway.lastRequest?.relativePath)
            assertEquals("image/jpeg", gateway.lastRequest?.mimeType)
            assertEquals("ZIDEE_IMG_20261118_WA_STORY.jpg", gateway.lastRequest?.displayName)
            assertEquals(listOf("insert_pending", "open_stream", "clear_pending", "scan"), gateway.events)
            assertArrayEquals(payload, gateway.writtenBytes.toByteArray())
            assertFalse("Staging cache file must be deleted after save", stagedFile.exists())
        }

    @Test
    fun `saves video to Movies ZideeResolution with video mp4 mime type`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway()
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))

            val stagedFile = tempFolder.newFile("staged.mp4").apply { writeBytes(byteArrayOf(9, 8, 7)) }
            val staged = StagedOutput(stagedFile.absolutePath, MediaType.VIDEO, PixelSize(1080, 1920), 3L, 30_000L, 1, 2)

            val result = saver.saveToGallery(staged, "ZIDEE_VID_20261118_WA_STORY_part01.mp4")

            assertTrue(result is AppResult.Success)
            assertEquals("Movies/ZideeResolution", gateway.lastRequest?.relativePath)
            assertEquals("video/mp4", gateway.lastRequest?.mimeType)
        }

    @Test
    fun `rolls back pending entry and deletes staging file when write fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway(failOnWrite = true)
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))

            val stagedFile = tempFolder.newFile("broken.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val staged = StagedOutput(stagedFile.absolutePath, MediaType.PHOTO, PixelSize(1080, 1080), 3L, null, 1, 1)

            val result = saver.saveToGallery(staged, "ZIDEE_IMG_FAIL.jpg")

            assertTrue(result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.StorageWrite)
            assertTrue(gateway.events.contains("delete_pending"))
            assertFalse(gateway.events.contains("clear_pending"))
            assertFalse(stagedFile.exists())
        }

    @Test
    fun `fails and rolls back without scanning when markCommitted updates zero rows`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway(publishedRowCount = 0)
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))

            val stagedFile = tempFolder.newFile("vanished.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }
            val staged = StagedOutput(stagedFile.absolutePath, MediaType.PHOTO, PixelSize(1080, 1920), 3L, null, 1, 1)

            val result = saver.saveToGallery(staged, "ZIDEE_IMG_ZERO_ROWS.jpg")

            assertTrue("Expected failure when IS_PENDING=0 updates 0 rows", result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.StorageWrite)
            assertEquals(
                listOf("insert_pending", "open_stream", "clear_pending", "delete_pending"),
                gateway.events,
            )
            assertFalse("Must not scan unpublished row", gateway.events.contains("scan"))
            assertFalse("Staging cache file must still be deleted", stagedFile.exists())
        }

    @Test
    fun `optimize use case emits Failed and records zero history when publish updates zero rows`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway(publishedRowCount = 0)
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))
            val stagedFile = tempFolder.newFile("usecase_zero.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val staged = StagedOutput(stagedFile.absolutePath, MediaType.PHOTO, PixelSize(1080, 1920), 4L, null, 1, 1)

            val recordedItems = mutableListOf<OptimizedMediaItem>()
            val historyRepo =
                object : OptimizationHistoryRepository {
                    override fun observeHistory(): Flow<List<OptimizedMediaItem>> = flowOf(recordedItems)

                    override suspend fun record(items: List<OptimizedMediaItem>): AppResult<Unit> {
                        recordedItems += items
                        return AppResult.Success(Unit)
                    }

                    override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
                }
            val fakeEngine =
                object : MediaOptimizerEngine {
                    override fun render(
                        sourceUri: MediaUri,
                        spec: OptimalMediaSpec,
                    ): Flow<EngineProgress> = flowOf(EngineProgress.Completed(listOf(staged)))
                }

            val useCase =
                OptimizeMediaUseCase(
                    calculateOptimalSpec = CalculateOptimalSpecUseCase(),
                    engine = fakeEngine,
                    storage = saver,
                    history = historyRepo,
                )
            val request =
                OptimizationRequest(
                    source = MediaMetadata(MediaUri("content://test/photo/1"), MediaType.PHOTO, 1080, 1920, 4096L),
                    platform = TargetPlatform.WA_STORY,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                )

            val statuses = useCase(request).toList()

            assertTrue(statuses.last() is OptimizationStatus.Failed)
            assertFalse(statuses.any { it is OptimizationStatus.Completed })
            assertTrue("History must remain empty when publish returns 0 rows", recordedItems.isEmpty())
            assertFalse(stagedFile.exists())
        }

    @Test
    fun `returns PermissionDenied and cleans staging when storage permission is rejected`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway(denyPermission = true)
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))

            val stagedFile = tempFolder.newFile("denied.jpg").apply { writeBytes(byteArrayOf(5, 6)) }
            val staged = StagedOutput(stagedFile.absolutePath, MediaType.PHOTO, PixelSize(1080, 1920), 2L, null, 1, 1)

            val result = saver.saveToGallery(staged, "ZIDEE_IMG_DENIED.jpg")

            assertTrue(result is AppResult.Failure)
            assertTrue((result as AppResult.Failure).error is AppError.PermissionDenied)
            assertFalse(gateway.events.contains("clear_pending"))
            assertFalse(stagedFile.exists())
        }

    @Test
    fun `sanitizes unsafe display names against path traversal`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway()
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))

            val stagedFile = tempFolder.newFile("safe.jpg").apply { writeBytes(byteArrayOf(42)) }
            val staged = StagedOutput(stagedFile.absolutePath, MediaType.PHOTO, PixelSize(1080, 1080), 1L, null, 1, 1)

            saver.saveToGallery(staged, "../../etc/ZIDEE_IMG.jpg")

            assertEquals("ZIDEE_IMG.jpg", gateway.lastRequest?.displayName)
        }

    @Test
    fun `discardStaged deletes staged file and empty parent job directory`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway()
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))

            val jobDir = tempFolder.newFolder("job_12345")
            val part1 = File(jobDir, "part_01.mp4").apply { writeBytes(byteArrayOf(1, 2)) }
            val part2 = File(jobDir, "part_02.mp4").apply { writeBytes(byteArrayOf(3, 4)) }

            saver.discardStaged(StagedOutput(part1.absolutePath, MediaType.VIDEO, PixelSize(1080, 1920), 2L, 30_000L, 1, 2))
            assertFalse(part1.exists())
            assertTrue("Non-empty job dir remains until last part deleted", jobDir.exists())

            saver.discardStaged(StagedOutput(part2.absolutePath, MediaType.VIDEO, PixelSize(1080, 1920), 2L, 15_000L, 2, 2))
            assertFalse(part2.exists())
            assertFalse("Empty job_ dir should be cleaned up", jobDir.exists())
        }

    @Test
    fun `exists delegates to gateway`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gateway = FakeMediaStoreGateway()
            val saver = ZideeMediaStoreSaver(gateway, TestDispatchers(dispatcher))

            assertTrue(saver.exists(MediaUri("content://media/external/images/1")))
            assertFalse(saver.exists(MediaUri("content://media/external/images/missing")))
        }

    private class FakeMediaStoreGateway(
        private val failOnWrite: Boolean = false,
        private val publishedRowCount: Int = 1,
        private val denyPermission: Boolean = false,
    ) : MediaStoreGateway {
        val events = mutableListOf<String>()
        var lastRequest: PendingMediaRequest? = null
        val writtenBytes = ByteArrayOutputStream()

        override fun insertPending(request: PendingMediaRequest): String {
            if (denyPermission) {
                throw SecurityException("WRITE_EXTERNAL_STORAGE denied")
            }
            lastRequest = request
            events += "insert_pending"
            return if (request.mediaType == MediaType.PHOTO) {
                "content://media/external/images/1"
            } else {
                "content://media/external/video/1"
            }
        }

        override fun openOutputStream(uri: String): OutputStream {
            events += "open_stream"
            if (failOnWrite) {
                return object : OutputStream() {
                    override fun write(b: Int): Unit = throw IOException("Disk full")
                }
            }
            return writtenBytes
        }

        override fun markCommitted(
            uri: String,
            displayName: String,
            mediaType: MediaType,
        ): Int {
            events += "clear_pending"
            return publishedRowCount
        }

        override fun scanIfNeeded(
            uri: String,
            displayName: String,
            mediaType: MediaType,
        ) {
            events += "scan"
        }

        override fun delete(uri: String) {
            events += "delete_pending"
        }

        override fun exists(uri: String): Boolean = !uri.endsWith("missing")
    }

    private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher = d
        override val io: CoroutineDispatcher = d
        override val default: CoroutineDispatcher = d
    }
}
