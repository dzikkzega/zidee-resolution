package com.zidee.ress.designsystem.component

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class Phase2DesignSystemAcceptanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun platformPresetSelectorMeasuresAtLeast48dpAndCapturesScreenshot() {
        var selectedId by mutableStateOf("WA_STORY")
        val options = samplePlatformOptions()

        composeRule.setContent {
            ZideeTheme {
                Box(
                    modifier =
                        Modifier
                            .testTag(CAPTURE_TAG)
                            .width(FIGCRAFT_PHONE_WIDTH)
                            .background(ZideeTheme.colors.bgSurface)
                            .padding(16.dp),
                ) {
                    PlatformPresetSelector(
                        options = options,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                        wrapItems = true,
                    )
                }
            }
        }

        val measurements =
            options.map { option ->
                val node = composeRule.onNodeWithTag("platform_preset_pill_${option.id}")
                assertMinTouchTarget("PlatformPresetPill(${option.id})", node)
            }

        composeRule.onNodeWithTag("platform_preset_pill_IG_STORY").performClick()
        composeRule.waitForIdle()
        assertEquals("IG_STORY", selectedId)

        saveCapture("platform_preset_selector.png")
        saveText("measured_platform_presets.txt", measurements.joinToString("\n"))
    }

    @Test
    fun canvasModeSegmentedControlMeasuresAtLeast48dpAndCapturesScreenshot() {
        var selectedId by mutableStateOf("BLUR_BG_9_16")
        val options = sampleCanvasOptions()

        composeRule.setContent {
            ZideeTheme {
                Box(
                    modifier =
                        Modifier
                            .testTag(CAPTURE_TAG)
                            .width(FIGCRAFT_PHONE_WIDTH)
                            .background(ZideeTheme.colors.bgSurface)
                            .padding(16.dp),
                ) {
                    CanvasModeSegmentedControl(
                        options = options,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                    )
                }
            }
        }

        val measurements =
            options.map { option ->
                val node = composeRule.onNodeWithTag("canvas_mode_segment_${option.id}")
                assertMinTouchTarget("CanvasModeSegment(${option.id})", node)
            }

        composeRule.onNodeWithTag("canvas_mode_segment_CROP_9_16").performClick()
        composeRule.waitForIdle()
        assertEquals("CROP_9_16", selectedId)

        saveCapture("canvas_mode_segmented_control.png")
        saveText("measured_canvas_modes.txt", measurements.joinToString("\n"))
    }

    @Test
    fun beforeAfterSplitSliderMatches40dpHandleAnd48dpHitAreaAndDragsSmoothly() {
        var splitFraction by mutableFloatStateOf(0.5f)

        composeRule.setContent {
            ZideeTheme {
                Box(
                    modifier =
                        Modifier
                            .testTag(CAPTURE_TAG)
                            .width(FIGCRAFT_PHONE_WIDTH)
                            .background(ZideeTheme.colors.bgCanvas)
                            .padding(16.dp),
                ) {
                    SampleSplitSliderHarness(
                        splitFraction = splitFraction,
                        onSplitChange = { splitFraction = it },
                    )
                }
            }
        }

        val dividerBounds =
            composeRule.onNodeWithTag(SplitSliderSpec.TAG_DIVIDER_LINE, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        val handleVisualBounds =
            composeRule.onNodeWithTag(SplitSliderSpec.TAG_HANDLE_VISUAL, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        val handleHitBounds =
            composeRule.onNodeWithTag(SplitSliderSpec.TAG_HANDLE_HIT_AREA, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

        val summary =
            "BeforeAfterSplitSlider measured: divider=${dividerBounds.width}, " +
                "handleVisual=${handleVisualBounds.width}x${handleVisualBounds.height}, " +
                "handleHit=${handleHitBounds.width}x${handleHitBounds.height}"
        Log.i(LOG_TAG, summary)

        assertDpNear("DividerLine.width", 2.dp, dividerBounds.width)
        assertDpNear("HandleVisual.width", 40.dp, handleVisualBounds.width)
        assertDpNear("HandleVisual.height", 40.dp, handleVisualBounds.height)
        assertTrue(
            "HandleHit.width >= 48dp (actual ${handleHitBounds.width})",
            handleHitBounds.width >= ZideeMinTouchTarget - DP_TOLERANCE,
        )
        assertTrue(
            "HandleHit.height >= 48dp (actual ${handleHitBounds.height})",
            handleHitBounds.height >= ZideeMinTouchTarget - DP_TOLERANCE,
        )

        saveCapture("split_slider_360dp.png")

        composeRule.onNodeWithTag(SplitSliderSpec.TAG_ROOT).performTouchInput {
            swipe(
                start = Offset(width * 0.5f, height * 0.5f),
                end = Offset(width * 0.85f, height * 0.5f),
                durationMillis = 200L,
            )
        }
        composeRule.waitForIdle()
        val rightFraction = splitFraction
        assertTrue("Expected splitFraction > 0.70 after right drag, actual=$rightFraction", rightFraction > 0.70f)

        composeRule.onNodeWithTag(SplitSliderSpec.TAG_ROOT).performTouchInput {
            swipe(
                start = Offset(width * 0.85f, height * 0.5f),
                end = Offset(width * 0.20f, height * 0.5f),
                durationMillis = 200L,
            )
        }
        composeRule.waitForIdle()
        val leftFraction = splitFraction
        assertTrue("Expected splitFraction < 0.35 after left drag, actual=$leftFraction", leftFraction < 0.35f)

        saveText(
            "measured_split_slider.txt",
            "$summary\ndragRightFraction=$rightFraction\ndragLeftFraction=$leftFraction\n",
        )
    }

    @Test
    fun advancedParameterPanelMeasuresAtLeast48dpAndCapturesCollapsedAndExpanded() {
        var expanded by mutableStateOf(false)
        val tuningState =
            AdvancedTuningUiState(
                sharpnessPercent = 28,
                defaultSharpnessPercent = 25,
                isManualBitrate = false,
            )

        composeRule.setContent {
            ZideeTheme {
                Column(
                    modifier =
                        Modifier
                            .testTag(CAPTURE_TAG)
                            .width(FIGCRAFT_PHONE_WIDTH)
                            .background(ZideeTheme.colors.bgCanvas)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                ) {
                    AdvancedParameterPanel(
                        expanded = expanded,
                        onToggleExpand = { expanded = !expanded },
                        summaryBadge = "Lanczos3 · USM 28% · x264",
                        state = tuningState,
                        onEvent = {},
                    )
                }
            }
        }

        val measurements = mutableListOf<String>()
        measurements.add(assertMinTouchTarget("AdvancedAccordionHeader", composeRule.onNodeWithTag("advanced_panel_header")))
        saveCapture("advanced_panel_collapsed.png")

        composeRule.onNodeWithTag("advanced_panel_header").performClick()
        composeRule.waitForIdle()
        assertTrue("Panel should expand on header click", expanded)

        measurements.add(assertMinTouchTarget("ResetAllButton", composeRule.onNodeWithTag("advanced_reset_all")))
        measurements.add(
            assertMinTouchTarget("OptionChip(Lanczos-3)", composeRule.onNodeWithTag("advanced_option_chip_Lanczos-3 (Tajam Maks)")),
        )
        measurements.add(assertMinTouchTarget("OptionChip(1080p)", composeRule.onNodeWithTag("advanced_option_chip_1080p Full HD")))
        measurements.add(
            assertMinTouchTarget("OptionChip(Lock 30 FPS)", composeRule.onNodeWithTag("advanced_option_chip_Lock 30 FPS")),
        )
        measurements.add(
            assertMinTouchTarget("OptionChip(Bitrate Auto)", composeRule.onNodeWithTag("advanced_option_chip_Bitrate Auto")),
        )
        measurements.add(
            assertMinTouchTarget(
                "EncoderTile(CPU)",
                composeRule.onNodeWithTag("advanced_encoder_tile_Kualitas Maksimal (CPU · libx264)"),
            ),
        )
        measurements.add(
            assertMinTouchTarget(
                "EncoderTile(GPU)",
                composeRule.onNodeWithTag("advanced_encoder_tile_Mode Cepat (Hardware GPU · MediaCodec)"),
            ),
        )
        measurements.add(
            assertMinTouchTarget(
                "ToggleRow(HDR)",
                composeRule.onNodeWithTag("advanced_toggle_row_Fix Warna HDR10 / Display P3 → sRGB SDR"),
            ),
        )

        saveCapture("advanced_panel_expanded.png")
        saveText("measured_advanced_panel.txt", measurements.joinToString("\n"))
    }

    private fun assertMinTouchTarget(
        label: String,
        node: SemanticsNodeInteraction,
    ): String {
        val bounds = node.getUnclippedBoundsInRoot()
        val line = "TouchTarget measured [$label]: ${bounds.width} x ${bounds.height}"
        Log.i(LOG_TAG, line)
        assertTrue(
            "$label width ${bounds.width} < $ZideeMinTouchTarget",
            bounds.width >= ZideeMinTouchTarget - DP_TOLERANCE,
        )
        assertTrue(
            "$label height ${bounds.height} < $ZideeMinTouchTarget",
            bounds.height >= ZideeMinTouchTarget - DP_TOLERANCE,
        )
        return line
    }

    private fun assertDpNear(
        label: String,
        expected: Dp,
        actual: Dp,
    ) {
        val diff = kotlin.math.abs(expected.value - actual.value)
        assertTrue("$label expected=$expected actual=$actual (diff=${diff}dp)", diff <= DP_TOLERANCE.value)
    }

    private fun saveCapture(fileName: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(CAPTURE_TAG).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeEvidenceBytes(context, fileName, "image/png") { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.i(LOG_TAG, "Saved PNG evidence: $fileName (${bitmap.width}x${bitmap.height})")
    }

    private fun saveText(
        fileName: String,
        content: String,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeEvidenceBytes(context, fileName, "text/plain") { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    companion object {
        private const val LOG_TAG = "Phase2Evidence"
        private const val CAPTURE_TAG = "phase2_capture_root"
        private val FIGCRAFT_PHONE_WIDTH = 360.dp
        private val DP_TOLERANCE = 0.75.dp

        private fun writeEvidenceBytes(
            context: Context,
            fileName: String,
            mimeType: String,
            writer: (java.io.OutputStream) -> Unit,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values =
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase2_evidence")
                    }
                val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                requireNotNull(context.contentResolver.openOutputStream(uri)).use(writer)
            } else {
                val dir = File(requireNotNull(context.getExternalFilesDir(null)), "phase2_evidence")
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, fileName)).use(writer)
            }
        }

        private fun samplePlatformOptions(): List<PlatformPresetOption> =
            listOf(
                PlatformPresetOption(id = "WA_STORY", label = "WA Story"),
                PlatformPresetOption(id = "WA_CHAT", label = "WA Chat"),
                PlatformPresetOption(id = "IG_STORY", label = "IG Story"),
                PlatformPresetOption(id = "IG_POST", label = "IG Post"),
                PlatformPresetOption(id = "IG_CHAT", label = "DM"),
            )

        private fun sampleCanvasOptions(): List<CanvasModeOption> =
            listOf(
                CanvasModeOption("ORIGINAL", "Rasio Asli", CanvasModeIconKind.ORIGINAL),
                CanvasModeOption("BLUR_BG_9_16", "Blur BG 9:16", CanvasModeIconKind.BLUR_BG_9_16),
                CanvasModeOption("CROP_9_16", "Crop 9:16", CanvasModeIconKind.CROP_9_16),
            )
    }
}

@Composable
private fun SampleSplitSliderHarness(
    splitFraction: Float,
    onSplitChange: (Float) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    BeforeAfterSplitSlider(
        splitFraction = splitFraction,
        onSplitFractionChange = onSplitChange,
        bottomStatusText = "Kanvas 9:16 · Blur BG Radius: 35px",
        modifier =
            Modifier
                .fillMaxWidth()
                .height(380.dp),
        beforeContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colors.bgElevated),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("[ TANPA ZIDEE RESS ]", style = typography.metricMono, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Resize kasar WA/IG", style = typography.metricMono, color = colors.textSecondary)
                    Text("• 60fps pecah/kotak", style = typography.metricMono, color = colors.textSecondary)
                    Text("• Warna HDR pucat", style = typography.metricMono, color = colors.textSecondary)
                }
            }
        },
        afterContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colors.bgSurface),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("[ ZIDEE RESS HD+ ]", style = typography.metricMono, color = colors.accentAmberBright)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("✓ Lanczos-3 1080×1920", style = typography.metricMono, color = colors.contrastWhite)
                    Text("✓ Unsharp Mask +28%", style = typography.metricMono, color = colors.contrastWhite)
                    Text("✓ 30fps SDR Tajam", style = typography.metricMono, color = colors.statusSuccess)
                }
            }
        },
    )
}
