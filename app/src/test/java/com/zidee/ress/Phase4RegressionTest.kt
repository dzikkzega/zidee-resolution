package com.zidee.ress

import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.core.result.AppResult
import com.zidee.ress.data.mediastore.MediaStoreGateway
import com.zidee.ress.data.mediastore.PendingMediaRequest
import com.zidee.ress.data.mediastore.ZideeMediaStoreSaver
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.StagedOutput
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.repository.MediaMetadataRepository
import com.zidee.ress.domain.repository.MediaOptimizerEngine
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.repository.UserPreferencesRepository
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import com.zidee.ress.presentation.quickshare.QuickShareUiEffect
import com.zidee.ress.presentation.quickshare.QuickShareUiEvent
import com.zidee.ress.presentation.quickshare.QuickShareViewModel
import com.zidee.ress.presentation.share.DirectShareTarget
import com.zidee.ress.presentation.studio.StudioEditorUiEffect
import com.zidee.ress.presentation.studio.StudioEditorUiEvent
import com.zidee.ress.presentation.studio.StudioEditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4RegressionTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `legacy storage write permission gate applies strictly to API 26 to 28`() {
        assertTrue(requiresLegacyStorageWritePermission(26))
        assertTrue(requiresLegacyStorageWritePermission(27))
        assertTrue(requiresLegacyStorageWritePermission(28))
        assertFalse(requiresLegacyStorageWritePermission(29))
        assertFalse(requiresLegacyStorageWritePermission(34))
        assertFalse(requiresLegacyStorageWritePermission(35))
    }

    @Test
    fun `studio save and share emits error with no history or share intent when publish updates zero rows`() =
        runWithMainDispatcher { dispatcher ->
            val harness = buildHarness(dispatcher, publishedRowCount = 0)
            val effects = mutableListOf<StudioEditorUiEffect>()
            val collector = launch { harness.studioVm.effect.collect { effects += it } }

            harness.studioVm.onEvent(StudioEditorUiEvent.LoadSources(listOf(harness.sourceUri), TargetPlatform.WA_STORY))
            advanceUntilIdle()
            harness.studioVm.onEvent(StudioEditorUiEvent.SaveAndShare(DirectShareTarget.WHATSAPP))
            advanceUntilIdle()
            collector.cancel()

            assertNotNull("Expected user-visible error message", harness.studioVm.state.value.errorMessage)
            assertTrue("History must remain empty when publish returns 0 rows", harness.recordedHistory.isEmpty())
            assertTrue("No LaunchShare or SavedSuccessfully effect may be emitted", effects.isEmpty())
        }

    @Test
    fun `quick share emits error with no history or share intent when publish updates zero rows`() =
        runWithMainDispatcher { dispatcher ->
            val harness = buildHarness(dispatcher, publishedRowCount = 0)
            val effects = mutableListOf<QuickShareUiEffect>()
            val collector = launch { harness.quickShareVm.effect.collect { effects += it } }

            harness.quickShareVm.onEvent(QuickShareUiEvent.LoadSources(listOf(harness.sourceUri)))
            advanceUntilIdle()
            harness.quickShareVm.onEvent(QuickShareUiEvent.ClickSaveAndShare(DirectShareTarget.WHATSAPP))
            advanceUntilIdle()
            collector.cancel()

            assertNotNull("Expected user-visible error message", harness.quickShareVm.state.value.errorMessage)
            assertTrue("History must remain empty when publish returns 0 rows", harness.recordedHistory.isEmpty())
            assertTrue("No share intent effect may be emitted", effects.isEmpty())
        }

    @Test
    fun `save and share publishes to MediaStore and records history strictly before emitting share intent`() =
        runWithMainDispatcher { dispatcher ->
            val harness = buildHarness(dispatcher, publishedRowCount = 1)
            val collector =
                launch {
                    harness.quickShareVm.effect.collect { effect ->
                        if (effect is QuickShareUiEffect.LaunchTargetAppShare) {
                            harness.timeline += "share_intent"
                        }
                    }
                }

            harness.quickShareVm.onEvent(QuickShareUiEvent.LoadSources(listOf(harness.sourceUri)))
            advanceUntilIdle()
            harness.quickShareVm.onEvent(QuickShareUiEvent.ClickSaveAndShare(DirectShareTarget.WHATSAPP))
            advanceUntilIdle()
            collector.cancel()

            assertEquals(
                listOf("insert_pending", "mark_committed", "record_history", "share_intent"),
                harness.timeline,
            )
            assertEquals(1, harness.recordedHistory.size)
        }

    private fun runWithMainDispatcher(block: suspend TestScope.(CoroutineDispatcher) -> Unit) =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                block(dispatcher)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun buildHarness(
        dispatcher: CoroutineDispatcher,
        publishedRowCount: Int,
    ): RegressionHarness {
        val timeline = mutableListOf<String>()
        val recordedHistory = mutableListOf<OptimizedMediaItem>()
        val sourceUri = MediaUri("content://test/source/1")
        val metadata = MediaMetadata(sourceUri, MediaType.PHOTO, 1080, 1920, 4096L)

        val gateway =
            object : MediaStoreGateway {
                override fun insertPending(request: PendingMediaRequest): String {
                    timeline += "insert_pending"
                    return "content://media/external_primary/images/media/99"
                }

                override fun openOutputStream(uri: String): OutputStream = ByteArrayOutputStream()

                override fun markCommitted(
                    uri: String,
                    displayName: String,
                    mediaType: MediaType,
                ): Int {
                    timeline += "mark_committed"
                    return publishedRowCount
                }

                override fun scanIfNeeded(
                    uri: String,
                    displayName: String,
                    mediaType: MediaType,
                ) = Unit

                override fun delete(uri: String) = Unit

                override fun exists(uri: String): Boolean = true
            }
        val dispatchers =
            object : DispatcherProvider {
                override val main: CoroutineDispatcher = dispatcher
                override val io: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
            }
        val saver = ZideeMediaStoreSaver(gateway, dispatchers)
        val historyRepo =
            object : OptimizationHistoryRepository {
                override fun observeHistory(): Flow<List<OptimizedMediaItem>> = flowOf(recordedHistory)

                override suspend fun record(items: List<OptimizedMediaItem>): AppResult<Unit> {
                    timeline += "record_history"
                    recordedHistory += items
                    return AppResult.Success(Unit)
                }

                override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
            }
        val engine =
            object : MediaOptimizerEngine {
                override fun render(
                    sourceUri: MediaUri,
                    spec: OptimalMediaSpec,
                ): Flow<EngineProgress> {
                    val stagedFile = tempFolder.newFile("phase4_${System.nanoTime()}.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
                    val staged = StagedOutput(stagedFile.absolutePath, MediaType.PHOTO, PixelSize(1080, 1920), 3L, null, 1, 1)
                    return flowOf(EngineProgress.Completed(listOf(staged)))
                }
            }
        val metadataRepo =
            object : MediaMetadataRepository {
                override suspend fun inspect(uri: MediaUri): AppResult<MediaMetadata> = AppResult.Success(metadata)
            }
        val prefsRepo =
            object : UserPreferencesRepository {
                private val prefs = MutableStateFlow(UserPreferences())

                override fun observePreferences(): Flow<UserPreferences> = prefs

                override suspend fun setLastSelection(
                    platform: TargetPlatform,
                    aspectMode: CanvasAspectMode,
                ): AppResult<Unit> {
                    prefs.value = prefs.value.copy(lastSelectedPlatform = platform, lastSelectedAspectMode = aspectMode)
                    return AppResult.Success(Unit)
                }

                override suspend fun setPreferredEncoder(encoder: VideoEncoder): AppResult<Unit> {
                    prefs.value = prefs.value.copy(preferredVideoEncoder = encoder)
                    return AppResult.Success(Unit)
                }

                override suspend fun setDefaultSharpnessPercent(percent: Int?): AppResult<Unit> = AppResult.Success(Unit)
            }

        val calc = CalculateOptimalSpecUseCase()
        val optimize = OptimizeMediaUseCase(calc, engine, saver, historyRepo)
        return RegressionHarness(
            sourceUri = sourceUri,
            timeline = timeline,
            recordedHistory = recordedHistory,
            studioVm = StudioEditorViewModel(calc, optimize, metadataRepo, prefsRepo),
            quickShareVm = QuickShareViewModel(calc, optimize, metadataRepo, prefsRepo),
        )
    }

    private data class RegressionHarness(
        val sourceUri: MediaUri,
        val timeline: MutableList<String>,
        val recordedHistory: MutableList<OptimizedMediaItem>,
        val studioVm: StudioEditorViewModel,
        val quickShareVm: QuickShareViewModel,
    )
}
