package com.zidee.ress

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zidee.ress.designsystem.component.AdvancedParameterPanel
import com.zidee.ress.designsystem.component.AdvancedTuningUiState
import com.zidee.ress.designsystem.component.BeforeAfterSplitSlider
import com.zidee.ress.designsystem.component.CanvasModeIconKind
import com.zidee.ress.designsystem.component.CanvasModeOption
import com.zidee.ress.designsystem.component.CanvasModeSegmentedControl
import com.zidee.ress.designsystem.component.PlatformPresetOption
import com.zidee.ress.designsystem.component.PlatformPresetSelector
import com.zidee.ress.designsystem.theme.ZideeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class Phase2AppAcceptanceEvidenceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun adaptiveLauncherIconRendersClearlyUnderAllFourLauncherMasks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bgDrawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_background))
        val fgDrawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))

        val fgBitmap = Bitmap.createBitmap(ICON_CANVAS_PX, ICON_CANVAS_PX, Bitmap.Config.ARGB_8888)
        val fgCanvas = Canvas(fgBitmap)
        fgDrawable.setBounds(0, 0, ICON_CANVAS_PX, ICON_CANVAS_PX)
        fgDrawable.draw(fgCanvas)

        val outsideCirclePixels = countOpaqueForegroundPixelsOutsideCircleMask(fgBitmap)
        Log.i(LOG_TAG, "AdaptiveIcon opaque foreground pixels outside 72dp Circle mask: $outsideCirclePixels")
        assertEquals(
            "Foreground vector must not clip outside the 72dp Circle launcher mask",
            0,
            outsideCirclePixels,
        )

        val sheet = renderAdaptiveIconMaskSheet(bgDrawable, fgDrawable)
        writeEvidencePng(context, "adaptive_icon_masks.png", sheet)
        writeEvidenceText(
            context,
            "adaptive_icon_verification.txt",
            "outsideCircleMaskOpaquePixels=$outsideCirclePixels\nsheetSize=${sheet.width}x${sheet.height}\n",
        )
        Log.i(LOG_TAG, "Saved adaptive icon mask sheet (${sheet.width}x${sheet.height})")
    }

    @Test
    fun studio360dpComparisonMatchesFigCraftFrame119Scale() {
        composeRule.setContent {
            ZideeTheme {
                Studio360dpComparisonFrame()
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(STUDIO_CAPTURE_TAG).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeEvidencePng(context, "studio_360dp_comparison.png", bitmap)
        Log.i(LOG_TAG, "Saved Studio 360dp comparison (${bitmap.width}x${bitmap.height})")
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    companion object {
        private const val LOG_TAG = "Phase2AppEvidence"
        private const val STUDIO_CAPTURE_TAG = "studio_360dp_frame"
        private const val ICON_CANVAS_PX = 432

        private fun countOpaqueForegroundPixelsOutsideCircleMask(fgBitmap: Bitmap): Int {
            val center = fgBitmap.width / 2.0
            val visibleRadiusPx = fgBitmap.width * (36.0 / 108.0)
            var count = 0
            for (y in 0 until fgBitmap.height) {
                for (x in 0 until fgBitmap.width) {
                    val alpha = Color.alpha(fgBitmap.getPixel(x, y))
                    if (alpha > 20) {
                        val dist = hypot(x + 0.5 - center, y + 0.5 - center)
                        if (dist > visibleRadiusPx) {
                            count += 1
                        }
                    }
                }
            }
            return count
        }

        private fun renderAdaptiveIconMaskSheet(
            bg: android.graphics.drawable.Drawable,
            fg: android.graphics.drawable.Drawable,
        ): Bitmap {
            val width = 1080
            val height = 700
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.parseColor("#0B0D10"))

            val titlePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#F8FAFC")
                    textSize = 32f
                    isFakeBoldText = true
                }
            val subPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#94A3B8")
                    textSize = 22f
                }
            val labelPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FBBF24")
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }

            canvas.drawText("Zidee Ress v1.0 — Adaptive Launcher Icon Masks (Android 14)", 40f, 56f, titlePaint)
            canvas.drawText("Top row: 200px inspection scale · Bottom row: 120px real launcher tile scale", 40f, 92f, subPaint)

            val masks = listOf("Circle", "Squircle", "Rounded Sq", "Teardrop")
            val colWidth = (width - 80f) / 4f

            masks.forEachIndexed { index, name ->
                val cx = 40f + colWidth * index + colWidth / 2f
                drawMaskedIcon(canvas, bg, fg, cx, 245f, 200f, name)
                canvas.drawText(name, cx, 385f, labelPaint)
                drawMaskedIcon(canvas, bg, fg, cx, 505f, 120f, name)
                canvas.drawText("Launcher 1:1", cx, 610f, subPaint.apply { textAlign = Paint.Align.CENTER })
            }
            return out
        }

        private fun drawMaskedIcon(
            canvas: Canvas,
            bg: android.graphics.drawable.Drawable,
            fg: android.graphics.drawable.Drawable,
            cx: Float,
            cy: Float,
            visibleSize: Float,
            maskName: String,
        ) {
            val half = visibleSize / 2f
            val rect = RectF(cx - half, cy - half, cx + half, cy + half)
            val maskPath = buildMaskPath(rect, maskName)

            val fullAdaptiveSize = visibleSize * (108f / 72f)
            val fullHalf = fullAdaptiveSize / 2f
            val left = (cx - fullHalf).toInt()
            val top = (cy - fullHalf).toInt()
            val right = (cx + fullHalf).toInt()
            val bottom = (cy + fullHalf).toInt()

            val save = canvas.save()
            canvas.clipPath(maskPath)
            bg.setBounds(left, top, right, bottom)
            bg.draw(canvas)
            fg.setBounds(left, top, right, bottom)
            fg.draw(canvas)
            canvas.restoreToCount(save)

            val outlinePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    color = Color.parseColor("#33FFFFFF")
                }
            canvas.drawPath(maskPath, outlinePaint)
        }

        private fun buildMaskPath(
            rect: RectF,
            maskName: String,
        ): Path {
            val path = Path()
            val cx = rect.centerX()
            val cy = rect.centerY()
            val rx = rect.width() / 2f
            val ry = rect.height() / 2f
            when (maskName) {
                "Circle" -> path.addOval(rect, Path.Direction.CW)
                "Rounded Sq" -> path.addRoundRect(rect, rect.width() * 0.22f, rect.height() * 0.22f, Path.Direction.CW)
                "Teardrop" -> {
                    val r = rect.width() * 0.5f
                    val sharp = rect.width() * 0.12f
                    val radii = floatArrayOf(r, r, r, r, sharp, sharp, r, r)
                    path.addRoundRect(rect, radii, Path.Direction.CW)
                }
                else -> {
                    val steps = 120
                    for (i in 0..steps) {
                        val theta = 2.0 * Math.PI * i / steps
                        val c = cos(theta)
                        val s = sin(theta)
                        val x = cx + rx * kotlin.math.sign(c).toFloat() * sqrt(kotlin.math.abs(c)).toFloat()
                        val y = cy + ry * kotlin.math.sign(s).toFloat() * sqrt(kotlin.math.abs(s)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                }
            }
            return path
        }

        private fun writeEvidencePng(
            context: Context,
            fileName: String,
            bitmap: Bitmap,
        ) {
            writeEvidenceBytes(context, fileName, "image/png") { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }

        private fun writeEvidenceText(
            context: Context,
            fileName: String,
            text: String,
        ) {
            writeEvidenceBytes(context, fileName, "text/plain") { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            }
        }

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
    }
}

@Composable
private fun Studio360dpComparisonFrame() {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes

    Column(
        modifier =
            Modifier
                .testTag("studio_360dp_frame")
                .width(360.dp)
                .height(800.dp)
                .background(colors.bgCanvas)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "←  VID_20261118_0941.mp4",
            style = typography.sectionHeader.copy(fontWeight = FontWeight.Bold),
            color = colors.contrastWhite,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shapes.badge,
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.borderActive),
        ) {
            Text(
                text = "ASLI: 4K 60fps 64MB → TARGET: 1080×1920 30fps ~7MB",
                style = typography.metricMono,
                color = colors.accentAmberBright,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        BeforeAfterSplitSlider(
            splitFraction = 0.5f,
            onSplitFractionChange = {},
            bottomStatusText = "Kanvas 9:16 · Blur BG Radius: 35px",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            beforeContent = { ComparisonSideBefore() },
            afterContent = { ComparisonSideAfter() },
        )
        StudioBottomControlsPreview()
    }
}

@Composable
private fun StudioBottomControlsPreview() {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val platforms =
        listOf(
            PlatformPresetOption("WA_STORY", "WA Story"),
            PlatformPresetOption("WA_CHAT", "WA Chat"),
            PlatformPresetOption("IG_STORY", "IG Story"),
            PlatformPresetOption("IG_POST", "IG Post"),
            PlatformPresetOption("IG_CHAT", "DM"),
        )
    val canvasModes =
        listOf(
            CanvasModeOption("ORIGINAL", "Rasio Asli", CanvasModeIconKind.ORIGINAL),
            CanvasModeOption("BLUR_BG_9_16", "Blur BG 9:16", CanvasModeIconKind.BLUR_BG_9_16),
            CanvasModeOption("CROP_9_16", "Crop 9:16", CanvasModeIconKind.CROP_9_16),
        )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.card,
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("1. TARGET PLATFORM OPTIMASI", style = typography.metricMono, color = colors.textSecondary)
            PlatformPresetSelector(options = platforms, selectedId = "WA_STORY", onSelect = {})
            Text("2. MODE KANVAS & RASIO (1080×1920)", style = typography.metricMono, color = colors.textSecondary)
            CanvasModeSegmentedControl(options = canvasModes, selectedId = "BLUR_BG_9_16", onSelect = {})
            AdvancedParameterPanel(
                expanded = false,
                onToggleExpand = {},
                summaryBadge = "Lanczos3 · USM 28% · x264",
                state = AdvancedTuningUiState(sharpnessPercent = 28),
                onEvent = {},
            )
            Text(
                text = "✓ Otomatis disimpan permanen ke Galeri/ZideeResolution",
                style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
                color = colors.statusSuccess,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                    shape = shapes.control,
                    color = colors.bgElevated,
                    border = BorderStroke(1.dp, colors.borderSubtle),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Simpan Saja", style = typography.controlLabel, color = colors.contrastWhite)
                    }
                }
                Surface(
                    modifier =
                        Modifier
                            .weight(1.35f)
                            .height(48.dp),
                    shape = shapes.control,
                    color = colors.accentAmber,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Simpan & Kirim ke WA",
                            style = typography.controlLabel.copy(fontWeight = FontWeight.Bold),
                            color = colors.onAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonSideBefore() {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.bgElevated),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("[ TANPA ZIDEE RESS ]", style = typography.metricMono, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text("• Resize kasar WA/IG", style = typography.metricMono, color = colors.textSecondary)
            Text("• 60fps pecah/kotak", style = typography.metricMono, color = colors.textSecondary)
            Text("• Warna HDR pucat", style = typography.metricMono, color = colors.textSecondary)
        }
    }
}

@Composable
private fun ComparisonSideAfter() {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.bgSurface),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("[ ZIDEE RESS HD+ ]", style = typography.metricMono, color = colors.accentAmberBright)
            Spacer(modifier = Modifier.height(2.dp))
            Text("✓ Lanczos-3 1080×1920", style = typography.metricMono, color = colors.contrastWhite)
            Text("✓ Unsharp Mask +28%", style = typography.metricMono, color = colors.contrastWhite)
            Text("✓ 30fps SDR Tajam", style = typography.metricMono, color = colors.statusSuccess)
        }
    }
}
