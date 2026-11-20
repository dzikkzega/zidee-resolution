package com.zidee.ress

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CropAdjustment
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.repository.EngineProgress
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.engine.StagingDirectoryCleaner
import com.zidee.ress.engine.photo.PhotoProcessingPipeline
import com.zidee.ress.presentation.studio.StudioEditorScreen
import com.zidee.ress.presentation.studio.StudioEditorUiState
import com.zidee.ress.presentation.studio.StudioPreviewTags
import com.zidee.ress.presentation.studio.StudioProcessingState
import com.zidee.ress.presentation.studio.StudioQueueEntry
import com.zidee.ress.presentation.studio.StudioSplitPreviewCanvas
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class Phase5StudioPreviewAcceptanceTest : KoinComponent {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun p5_crop916PreviewMatchesExportAtTwoDensities_zoom1xAnd2x_leftCenterRight() {
        val workDir = File(checkNotNull(context.externalCacheDir), "phase5_crop_work").apply { mkdirs() }
        val calc = CalculateOptimalSpecUseCase()
        val dispatchers: DispatcherProvider = get()
        val pipeline = PhotoProcessingPipeline(context, dispatchers)
        val tempUris = mutableListOf<Uri>()
        val exportedBitmaps = mutableMapOf<String, Bitmap>()
        val previewCaptures = mutableMapOf<String, Bitmap>()
        val report = StringBuilder()
        report.appendLine("=== Phase 5 T-5.2 Crop 9:16 Preview vs Export Audit on ${Build.MODEL} (API ${Build.VERSION.SDK_INT}) ===")
        report.appendLine("Source: 4000x3000 landscape (Left=Red, Center=Green, Right=Blue), Preview thumbnail: 960x720")

        try {
            val sourceJpeg = createMarkedLandscapeJpeg(File(workDir, "p5_marked_4000x3000.jpg"))
            val sourceUri = insertInputIntoMediaStore(context, sourceJpeg, MediaType.PHOTO).also { tempUris += it }
            val thumbBitmap = decodeDownsampledThumbnail(sourceJpeg, maxWidth = 960)
            val thumbImageBitmap = thumbBitmap.asImageBitmap()
            val metadata = MediaMetadata(MediaUri(sourceUri.toString()), MediaType.PHOTO, 4000, 3000, sourceJpeg.length())
            val cropCases = defaultCropCases()
            val specsByCase = buildCropSpecs(calc, metadata, cropCases)

            exportAllCropCases(pipeline, metadata.uri, cropCases, specsByCase, exportedBitmaps)
            verifyPreviewAtDensities(
                metadata = metadata,
                thumbImageBitmap = thumbImageBitmap,
                cropCases = cropCases,
                specsByCase = specsByCase,
                exportedBitmaps = exportedBitmaps,
                previewCaptures = previewCaptures,
                report = report,
            )

            val contactSheet = buildCropComparisonSheet(cropCases.map { it.label }, previewCaptures, exportedBitmaps)
            savePhase5PngEvidence(context, "p5_crop_zoom1x_2x_comparison.png", contactSheet)
            contactSheet.recycle()
            savePhase5TextEvidence(context, "p5_crop_preview_vs_export_report.txt", report.toString())
            thumbBitmap.recycle()
        } finally {
            exportedBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
            previewCaptures.values.forEach { if (!it.isRecycled) it.recycle() }
            tempUris.forEach { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
            workDir.deleteRecursively()
            StagingDirectoryCleaner(context).deleteOrphanedOlderThan(maxAgeMs = 0L)
        }
    }

    @Test
    fun p5_beforeSidePreservesSourceColorsAndSeparatesSimulationLabels() {
        val swatches =
            listOf(
                ColorSwatch("Red", Color.rgb(230, 57, 70), 0.25f, 0.34f),
                ColorSwatch("Teal", Color.rgb(42, 157, 143), 0.75f, 0.34f),
                ColorSwatch("Blue", Color.rgb(29, 112, 232), 0.25f, 0.66f),
                ColorSwatch("Amber", Color.rgb(245, 158, 11), 0.75f, 0.66f),
            )
        val refBitmap = createColorReferenceChart(width = 540, height = 960, swatches = swatches)
        val refImageBitmap = refBitmap.asImageBitmap()
        val metadata = MediaMetadata(MediaUri("content://test/color_chart"), MediaType.PHOTO, 540, 960, 200_000L)
        val spec =
            CalculateOptimalSpecUseCase()(
                OptimizationRequest(
                    source = metadata,
                    platform = TargetPlatform.WA_STORY,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                ),
            )
        val uiState =
            StudioEditorUiState(
                queue = listOf(StudioQueueEntry(uri = metadata.uri, metadata = metadata, isLoading = false)),
                activeIndex = 0,
                selectedPlatform = TargetPlatform.WA_STORY,
                selectedAspectMode = CanvasAspectMode.ORIGINAL,
                activeSpec = spec,
                splitFraction = 1.0f,
            )

        var renderedAfter by mutableStateOf<ImageBitmap?>(null)
        composeRule.setContent {
            ZideeTheme {
                StudioSplitPreviewCanvas(
                    state = uiState,
                    onEvent = {},
                    showBottomPill = false,
                    previewBitmapOverride = refImageBitmap,
                    renderedAfterBitmap = renderedAfter,
                    modifier = Modifier.size(width = 216.dp, height = 384.dp),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("◄ ASLI").assertIsDisplayed()
        composeRule.onNodeWithText("SIMULASI PRATINJAU ►").assertIsDisplayed()

        val captured = composeRule.onNodeWithTag(StudioPreviewTags.BEFORE_STAGE).captureToImage().asAndroidBitmap()
        val report = StringBuilder()
        report.appendLine("=== Phase 5 T-5.2 Before ('ASLI') Color Fidelity & Label Separation ===")
        report.appendLine("livePreviewLabels=[◄ ASLI, SIMULASI PRATINJAU ►]")
        for (swatch in swatches) {
            val px = (captured.width * swatch.sampleU).roundToInt().coerceIn(0, captured.width - 1)
            val py = (captured.height * swatch.sampleV).roundToInt().coerceIn(0, captured.height - 1)
            val actual = captured.getPixel(px, py)
            val dR = abs(Color.red(actual) - Color.red(swatch.expectedColor))
            val dG = abs(Color.green(actual) - Color.green(swatch.expectedColor))
            val dB = abs(Color.blue(actual) - Color.blue(swatch.expectedColor))
            val expectedRgb =
                "${Color.red(swatch.expectedColor)},${Color.green(swatch.expectedColor)},${Color.blue(swatch.expectedColor)}"
            val actualRgb = "${Color.red(actual)},${Color.green(actual)},${Color.blue(actual)}"
            report.appendLine("swatch=${swatch.name} expected=($expectedRgb) actual=($actualRgb) delta=($dR,$dG,$dB)")
            assertTrue("ASLI side altered ${swatch.name} color: delta=($dR,$dG,$dB)", dR <= 3 && dG <= 3 && dB <= 3)
        }

        composeRule.runOnIdle { renderedAfter = refImageBitmap }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("HASIL NYATA HD+ ►").assertIsDisplayed()
        report.appendLine("renderedOutputLabel=[HASIL NYATA HD+ ►]")

        savePhase5PngEvidence(context, "p5_asli_color_chart_capture.png", captured)
        savePhase5TextEvidence(context, "p5_asli_color_fidelity_report.txt", report.toString())
        captured.recycle()
        refBitmap.recycle()
    }

    @Test
    fun p5_responsiveLayoutsAndPartialBatchRenderCleanly() {
        val savedItem =
            OptimizedMediaItem(
                id = "partial_1",
                mediaStoreUri = MediaUri("content://media/external/images/101"),
                displayName = "ZIDEE_PARTIAL_OK.jpg",
                mediaType = MediaType.PHOTO,
                platform = TargetPlatform.WA_STORY,
                aspectMode = CanvasAspectMode.CROP_9_16,
                originalSize = PixelSize(4000, 3000),
                outputSize = PixelSize(1080, 1920),
                originalSizeBytes = 4_200_000L,
                outputSizeBytes = 640_000L,
                durationMs = null,
                partIndex = 1,
                totalParts = 1,
                createdAtEpochMs = 1L,
            )
        val partialMessage = "1 file tersimpan, 1 gagal: Video rusak atau format tidak didukung."
        val partialState =
            StudioEditorUiState(
                processingState =
                    StudioProcessingState.PartialSuccess(
                        savedItems = listOf(savedItem),
                        failedCount = 1,
                        failureMessage = partialMessage,
                    ),
                errorMessage = partialMessage,
            )

        var layoutMode by mutableStateOf(LayoutBreakPoint.PHONE_PARTIAL)
        composeRule.setContent {
            val density =
                when (layoutMode) {
                    LayoutBreakPoint.PHONE_PARTIAL -> 2.0f
                    LayoutBreakPoint.TABLET_PORTRAIT -> 1.4f
                    LayoutBreakPoint.TABLET_LANDSCAPE -> 1.0f
                }
            val sizePair =
                when (layoutMode) {
                    LayoutBreakPoint.PHONE_PARTIAL -> 360.dp to 640.dp
                    LayoutBreakPoint.TABLET_PORTRAIT -> 640.dp to 720.dp
                    LayoutBreakPoint.TABLET_LANDSCAPE -> 960.dp to 600.dp
                }
            val uiState = if (layoutMode == LayoutBreakPoint.PHONE_PARTIAL) partialState else StudioEditorUiState()
            CompositionLocalProvider(LocalDensity provides Density(density = density, fontScale = 1f)) {
                ZideeTheme {
                    Box(modifier = Modifier.size(sizePair.first, sizePair.second).testTag(TAG_RESPONSIVE_ROOT)) {
                        StudioEditorScreen(
                            state = uiState,
                            historyItems = listOf(savedItem),
                            onEvent = {},
                            onBack = {},
                            onAddMoreMedia = {},
                            onReShareHistoryItem = {},
                            onShareCompletedItems = { _, _ -> },
                            onDoneToDashboard = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("⚠ Tersimpan Sebagian di Galeri").assertIsDisplayed()
        composeRule.onNodeWithText(partialMessage).assertIsDisplayed()
        composeRule.onNodeWithText("ZIDEE_PARTIAL_OK.jpg").assertIsDisplayed()
        captureResponsiveEvidence("p5_partial_batch_ui.png")

        composeRule.runOnIdle { layoutMode = LayoutBreakPoint.TABLET_PORTRAIT }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Zidee Ress Studio").assertIsDisplayed()
        composeRule.onNodeWithText("GESER GARIS ◄ ► · Simulasi Kanvas").assertIsDisplayed()
        captureResponsiveEvidence("p5_tablet_portrait_640dp.png")

        composeRule.runOnIdle { layoutMode = LayoutBreakPoint.TABLET_LANDSCAPE }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Zidee Ress Workstation").assertIsDisplayed()
        composeRule.onNodeWithText("SPLIT-SLIDER 120Hz · SIMULASI").assertIsDisplayed()
        captureResponsiveEvidence("p5_tablet_landscape_960dp.png")

        val summary =
            buildString {
                appendLine("=== Phase 5 Responsive Layout & Partial Batch UI Evidence ===")
                appendLine("phone_partial_batch=PASS (banner='⚠ Tersimpan Sebagian di Galeri', item='ZIDEE_PARTIAL_OK.jpg')")
                appendLine("phone_partial_error=$partialMessage")
                appendLine("tablet_portrait_640dp=PASS (header='Zidee Ress Studio', canvas='GESER GARIS ◄ ► · Simulasi Kanvas')")
                appendLine("tablet_landscape_960dp=PASS (header='Zidee Ress Workstation', canvas='SPLIT-SLIDER 120Hz · SIMULASI')")
            }
        savePhase5TextEvidence(context, "p5_responsive_and_partial_batch_report.txt", summary)
    }

    private fun captureResponsiveEvidence(fileName: String) {
        val captured = composeRule.onNodeWithTag(TAG_RESPONSIVE_ROOT).captureToImage().asAndroidBitmap()
        savePhase5PngEvidence(context, fileName, captured)
        captured.recycle()
    }

    private fun defaultCropCases(): List<CropCase> =
        listOf(
            CropCase("z1_left", CropAdjustment(panX = 0.0, panY = 0.5, zoom = 1.0), DominantChannel.RED),
            CropCase("z1_center", CropAdjustment(panX = 0.5, panY = 0.5, zoom = 1.0), DominantChannel.GREEN),
            CropCase("z1_right", CropAdjustment(panX = 1.0, panY = 0.5, zoom = 1.0), DominantChannel.BLUE),
            CropCase("z2_left", CropAdjustment(panX = 0.0, panY = 0.5, zoom = 2.0), DominantChannel.RED),
            CropCase("z2_center", CropAdjustment(panX = 0.5, panY = 0.5, zoom = 2.0), DominantChannel.GREEN),
            CropCase("z2_right", CropAdjustment(panX = 1.0, panY = 0.5, zoom = 2.0), DominantChannel.BLUE),
        )

    private fun buildCropSpecs(
        calc: CalculateOptimalSpecUseCase,
        metadata: MediaMetadata,
        cropCases: List<CropCase>,
    ): Map<CropCase, OptimalMediaSpec> =
        cropCases.associateWith { testCase ->
            calc(
                OptimizationRequest(
                    source = metadata,
                    platform = TargetPlatform.WA_STORY,
                    aspectMode = CanvasAspectMode.CROP_9_16,
                    crop = testCase.crop,
                ),
            )
        }

    private fun exportAllCropCases(
        pipeline: PhotoProcessingPipeline,
        sourceUri: MediaUri,
        cropCases: List<CropCase>,
        specsByCase: Map<CropCase, OptimalMediaSpec>,
        exportedBitmaps: MutableMap<String, Bitmap>,
    ) {
        runBlocking {
            for (testCase in cropCases) {
                val spec = checkNotNull(specsByCase[testCase])
                val done = pipeline.process(sourceUri, spec).last() as EngineProgress.Completed
                val exportedFile = File(done.outputs.first().cachePath)
                exportedBitmaps[testCase.label] = checkNotNull(BitmapFactory.decodeFile(exportedFile.absolutePath))
                exportedFile.delete()
            }
        }
    }

    private fun verifyPreviewAtDensities(
        metadata: MediaMetadata,
        thumbImageBitmap: ImageBitmap,
        cropCases: List<CropCase>,
        specsByCase: Map<CropCase, OptimalMediaSpec>,
        exportedBitmaps: Map<String, Bitmap>,
        previewCaptures: MutableMap<String, Bitmap>,
        report: StringBuilder,
    ) {
        val firstCase = cropCases.first()
        var activeDensity by mutableStateOf(2.0f)
        var activeState by mutableStateOf(buildCropUiState(metadata, checkNotNull(specsByCase[firstCase]), firstCase.crop))

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = activeDensity, fontScale = 1f)) {
                ZideeTheme {
                    StudioSplitPreviewCanvas(
                        state = activeState,
                        onEvent = {},
                        showBottomPill = false,
                        previewBitmapOverride = thumbImageBitmap,
                        modifier = Modifier.size(width = 180.dp, height = 320.dp),
                    )
                }
            }
        }

        for (densityValue in listOf(2.0f, 3.0f)) {
            for (testCase in cropCases) {
                val spec = checkNotNull(specsByCase[testCase])
                composeRule.runOnIdle {
                    activeDensity = densityValue
                    activeState = buildCropUiState(metadata, spec, testCase.crop)
                }
                composeRule.waitForIdle()
                val captured =
                    composeRule.onNodeWithTag(StudioPreviewTags.CROP_CANVAS_BEFORE)
                        .captureToImage()
                        .asAndroidBitmap()
                val exported = checkNotNull(exportedBitmaps[testCase.label])
                val metrics = comparePreviewVsExport(captured, exported)
                report.appendLine(
                    String.format(
                        Locale.US,
                        "density=%.1fx case=%-9s preview=%dx%d export=%dx%d meanAbsDiff=%.2f avgRGB=(%d,%d,%d) dominant=%s",
                        densityValue,
                        testCase.label,
                        captured.width,
                        captured.height,
                        exported.width,
                        exported.height,
                        metrics.meanAbsDiff,
                        metrics.avgR,
                        metrics.avgG,
                        metrics.avgB,
                        metrics.dominant,
                    ),
                )
                assertTrue(
                    "Expected dominant ${testCase.expectedDominant} for ${testCase.label} @ ${densityValue}x, " +
                        "got ${metrics.dominant} (${metrics.avgR},${metrics.avgG},${metrics.avgB})",
                    metrics.dominant == testCase.expectedDominant,
                )
                assertTrue(
                    "Preview vs export RGB diff too high for ${testCase.label} @ ${densityValue}x: ${metrics.meanAbsDiff}",
                    metrics.meanAbsDiff <= 8.0,
                )
                if (densityValue == 3.0f) {
                    previewCaptures[testCase.label] = captured
                } else {
                    captured.recycle()
                }
            }
        }
    }

    private fun buildCropUiState(
        metadata: MediaMetadata,
        spec: OptimalMediaSpec,
        crop: CropAdjustment,
    ): StudioEditorUiState =
        StudioEditorUiState(
            queue = listOf(StudioQueueEntry(uri = metadata.uri, metadata = metadata, isLoading = false)),
            activeIndex = 0,
            selectedPlatform = TargetPlatform.WA_STORY,
            selectedAspectMode = CanvasAspectMode.CROP_9_16,
            cropAdjustment = crop,
            activeSpec = spec,
            splitFraction = 1.0f,
        )

    private fun createMarkedLandscapeJpeg(target: File): File {
        val width = 4000
        val height = 3000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val third = width / 3
        val halfH = height / 2

        paint.color = Color.rgb(230, 40, 40)
        canvas.drawRect(0f, 0f, third.toFloat(), halfH.toFloat(), paint)
        paint.color = Color.rgb(175, 30, 30)
        canvas.drawRect(0f, halfH.toFloat(), third.toFloat(), height.toFloat(), paint)

        paint.color = Color.rgb(40, 200, 60)
        canvas.drawRect(third.toFloat(), 0f, (third * 2).toFloat(), halfH.toFloat(), paint)
        paint.color = Color.rgb(30, 150, 45)
        canvas.drawRect(third.toFloat(), halfH.toFloat(), (third * 2).toFloat(), height.toFloat(), paint)

        paint.color = Color.rgb(40, 90, 235)
        canvas.drawRect((third * 2).toFloat(), 0f, width.toFloat(), halfH.toFloat(), paint)
        paint.color = Color.rgb(30, 65, 180)
        canvas.drawRect((third * 2).toFloat(), halfH.toFloat(), width.toFloat(), height.toFloat(), paint)

        FileOutputStream(target).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 96, fos)
        }
        bitmap.recycle()
        return target
    }

    private fun decodeDownsampledThumbnail(
        jpegFile: File,
        maxWidth: Int,
    ): Bitmap {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val decoded = checkNotNull(BitmapFactory.decodeFile(jpegFile.absolutePath, opts))
        val targetH = (decoded.height.toDouble() * maxWidth / decoded.width).roundToInt()
        val scaled = Bitmap.createScaledBitmap(decoded, maxWidth, targetH, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun createColorReferenceChart(
        width: Int,
        height: Int,
        swatches: List<ColorSwatch>,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val halfW = width / 2f
        val halfH = height / 2f
        paint.color = swatches[0].expectedColor
        canvas.drawRect(0f, 0f, halfW, halfH, paint)
        paint.color = swatches[1].expectedColor
        canvas.drawRect(halfW, 0f, width.toFloat(), halfH, paint)
        paint.color = swatches[2].expectedColor
        canvas.drawRect(0f, halfH, halfW, height.toFloat(), paint)
        paint.color = swatches[3].expectedColor
        canvas.drawRect(halfW, halfH, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun comparePreviewVsExport(
        preview: Bitmap,
        exported: Bitmap,
    ): ComparisonMetrics {
        // Avoid top chrome badges/crop pill (v < 0.30), right split handle (u > 0.88), and exact 0.50 horizontal seam.
        val gridU = floatArrayOf(0.12f, 0.28f, 0.44f, 0.56f, 0.72f, 0.86f)
        val gridV = floatArrayOf(0.32f, 0.42f, 0.58f, 0.70f, 0.84f)
        var totalDiff = 0L
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        for (u in gridU) {
            for (v in gridV) {
                val pPixel = preview.getPixel((preview.width * u).toInt(), (preview.height * v).toInt())
                val ePixel = exported.getPixel((exported.width * u).toInt(), (exported.height * v).toInt())
                val pR = Color.red(pPixel)
                val pG = Color.green(pPixel)
                val pB = Color.blue(pPixel)
                totalDiff += abs(pR - Color.red(ePixel)) + abs(pG - Color.green(ePixel)) + abs(pB - Color.blue(ePixel))
                sumR += pR
                sumG += pG
                sumB += pB
                count++
            }
        }
        val avgR = (sumR / count).toInt()
        val avgG = (sumG / count).toInt()
        val avgB = (sumB / count).toInt()
        val dominant =
            when {
                avgR > avgG + 40 && avgR > avgB + 40 -> DominantChannel.RED
                avgG > avgR + 40 && avgG > avgB + 40 -> DominantChannel.GREEN
                avgB > avgR + 40 && avgB > avgG + 40 -> DominantChannel.BLUE
                else -> DominantChannel.MIXED
            }
        return ComparisonMetrics(
            meanAbsDiff = totalDiff.toDouble() / (count * 3),
            avgR = avgR,
            avgG = avgG,
            avgB = avgB,
            dominant = dominant,
        )
    }

    private fun buildCropComparisonSheet(
        labels: List<String>,
        previews: Map<String, Bitmap>,
        exports: Map<String, Bitmap>,
    ): Bitmap {
        val tileW = 180
        val tileH = 320
        val gap = 16
        val sheetW = labels.size * (tileW + gap) + gap
        val sheetH = tileH * 2 + gap * 3
        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(11, 13, 16))
        labels.forEachIndexed { idx, label ->
            val x = gap + idx * (tileW + gap)
            previews[label]?.let { bmp ->
                val scaled = Bitmap.createScaledBitmap(bmp, tileW, tileH, true)
                canvas.drawBitmap(scaled, x.toFloat(), gap.toFloat(), null)
                if (scaled !== bmp) scaled.recycle()
            }
            exports[label]?.let { bmp ->
                val scaled = Bitmap.createScaledBitmap(bmp, tileW, tileH, true)
                canvas.drawBitmap(scaled, x.toFloat(), (gap * 2 + tileH).toFloat(), null)
                if (scaled !== bmp) scaled.recycle()
            }
        }
        return sheet
    }

    private enum class DominantChannel { RED, GREEN, BLUE, MIXED }

    private enum class LayoutBreakPoint { PHONE_PARTIAL, TABLET_PORTRAIT, TABLET_LANDSCAPE }

    private data class CropCase(
        val label: String,
        val crop: CropAdjustment,
        val expectedDominant: DominantChannel,
    )

    private data class ColorSwatch(
        val name: String,
        val expectedColor: Int,
        val sampleU: Float,
        val sampleV: Float,
    )

    private data class ComparisonMetrics(
        val meanAbsDiff: Double,
        val avgR: Int,
        val avgG: Int,
        val avgB: Int,
        val dominant: DominantChannel,
    )

    companion object {
        private const val TAG_RESPONSIVE_ROOT = "phase5_responsive_root"
    }
}

internal fun savePhase5TextEvidence(
    context: Context,
    fileName: String,
    content: String,
) {
    val resolver = context.contentResolver
    val values =
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase5_evidence")
        }
    val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
}

internal fun savePhase5PngEvidence(
    context: Context,
    fileName: String,
    bitmap: Bitmap,
) {
    val resolver = context.contentResolver
    val values =
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase5_evidence")
        }
    val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
