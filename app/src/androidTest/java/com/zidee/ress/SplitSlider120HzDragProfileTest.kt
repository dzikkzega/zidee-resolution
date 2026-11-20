package com.zidee.ress

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zidee.ress.designsystem.component.BeforeAfterSplitSlider
import com.zidee.ress.designsystem.theme.ZideeTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.sin

/**
 * Production-clock (non-TestMonotonicFrameClock) 120Hz drag benchmark for [BeforeAfterSplitSlider].
 * Runs directly on the real Android Choreographer + RenderThread + Skia GPU pipeline and captures
 * live `dumpsys display` active refresh mode, `FrameMetrics`, and `dumpsys gfxinfo` frame timings.
 */
@RunWith(AndroidJUnit4::class)
class SplitSlider120HzDragProfileTest {
    @Test
    fun continuousDragOn120HzPanelStaysWithinFrameBudget() {
        var splitFraction by mutableFloatStateOf(0.5f)
        val totalDurationsMs = mutableListOf<Double>()
        val gpuDurationsMs = mutableListOf<Double>()
        val vsyncIntervalsMs = mutableListOf<Double>()
        var collectMetrics = false
        val metricsListener =
            Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                if (!collectMetrics) return@OnFrameMetricsAvailableListener
                val totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
                if (totalNs > 0L) {
                    totalDurationsMs.add(totalNs / 1_000_000.0)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val gpuNs = frameMetrics.getMetric(FrameMetrics.GPU_DURATION)
                    if (gpuNs > 0L) {
                        gpuDurationsMs.add(gpuNs / 1_000_000.0)
                    }
                }
            }

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val params = activity.window.attributes
                params.preferredRefreshRate = TARGET_REFRESH_HZ
                activity.window.attributes = params
                activity.window.addOnFrameMetricsAvailableListener(metricsListener, Handler(Looper.getMainLooper()))
                activity.setContent {
                    ZideeTheme {
                        BenchmarkSplitHost(
                            splitFraction = splitFraction,
                            onSplitChange = { splitFraction = it },
                        )
                    }
                }
            }

            Thread.sleep(600L)

            var running = true
            var lastFrameNanos = 0L
            scenario.onActivity { activity ->
                val rootView = activity.window.decorView
                val centerY = rootView.height * 0.5f
                val leftX = rootView.width * 0.14f
                val rightX = rootView.width * 0.86f
                val downTime = SystemClock.uptimeMillis()

                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, rootView.width * 0.5f, centerY, 0)
                rootView.dispatchTouchEvent(down)
                down.recycle()

                val choreographer = Choreographer.getInstance()
                val callback =
                    object : Choreographer.FrameCallback {
                        var step = 0

                        override fun doFrame(frameTimeNanos: Long) {
                            if (!running) return
                            if (step == WARMUP_FRAMES) {
                                collectMetrics = true
                                lastFrameNanos = frameTimeNanos
                            } else if (step > WARMUP_FRAMES && lastFrameNanos > 0L) {
                                vsyncIntervalsMs.add((frameTimeNanos - lastFrameNanos) / 1_000_000.0)
                                lastFrameNanos = frameTimeNanos
                            }
                            step += 1
                            val t = 0.5f + 0.5f * sin(step * 0.08).toFloat()
                            val x = leftX + (rightX - leftX) * t
                            val now = SystemClock.uptimeMillis()
                            val move = MotionEvent.obtain(downTime, now, MotionEvent.ACTION_MOVE, x, centerY, 0)
                            rootView.dispatchTouchEvent(move)
                            move.recycle()

                            if (step < WARMUP_FRAMES + MEASURED_FRAMES) {
                                choreographer.postFrameCallback(this)
                            } else {
                                val up = MotionEvent.obtain(downTime, now, MotionEvent.ACTION_UP, x, centerY, 0)
                                rootView.dispatchTouchEvent(up)
                                up.recycle()
                                running = false
                            }
                        }
                    }
                choreographer.postFrameCallback(callback)
            }

            Thread.sleep(350L)
            runShellCommand("dumpsys gfxinfo com.zidee.ress reset")
            Thread.sleep(700L)
            val activeDisplaySnapshot = captureActiveDisplayMode()

            val deadline = System.currentTimeMillis() + 8_000L
            while (running && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L)
            }
            Thread.sleep(150L)
            val gfxInfoSummary = captureGfxInfoSummary()

            scenario.onActivity { activity ->
                activity.window.removeOnFrameMetricsAvailableListener(metricsListener)
            }

            verifyAndSaveReport(vsyncIntervalsMs, totalDurationsMs, gpuDurationsMs, activeDisplaySnapshot, gfxInfoSummary)
        }
    }

    private fun verifyAndSaveReport(
        vsyncIntervalsMs: List<Double>,
        totalDurationsMs: List<Double>,
        gpuDurationsMs: List<Double>,
        activeDisplaySnapshot: String,
        gfxInfoSummary: String,
    ) {
        val sortedVsync = vsyncIntervalsMs.sorted()
        val sortedTotal = totalDurationsMs.sorted()
        val sortedGpu = gpuDurationsMs.sorted()
        assertTrue("Expected >= 150 VSync drag frames, actual=${sortedVsync.size}", sortedVsync.size >= 150)
        assertTrue("Expected >= 100 real FrameMetrics samples, actual=${sortedTotal.size}", sortedTotal.size >= 100)

        val vsyncP50 = percentile(sortedVsync, 0.50)
        val totalP50 = percentile(sortedTotal, 0.50)
        val gpuP50 = if (sortedGpu.isNotEmpty()) percentile(sortedGpu, 0.50) else 0.0

        val report =
            buildString {
                appendLine(
                    String.format(
                        Locale.US,
                        "120Hz VSync Drag Profile: frames=%d avg=%.2fms (%.1f fps) p50=%.2fms p90=%.2fms p95=%.2fms p99=%.2fms",
                        sortedVsync.size,
                        sortedVsync.average(),
                        1000.0 / sortedVsync.average(),
                        vsyncP50,
                        percentile(sortedVsync, 0.90),
                        percentile(sortedVsync, 0.95),
                        percentile(sortedVsync, 0.99),
                    ),
                )
                appendLine(
                    String.format(
                        Locale.US,
                        "120Hz FrameMetrics Total (UI+RT+GPU): frames=%d avg=%.2fms p50=%.2fms p90=%.2fms p95=%.2fms p99=%.2fms",
                        sortedTotal.size,
                        sortedTotal.average(),
                        totalP50,
                        percentile(sortedTotal, 0.90),
                        percentile(sortedTotal, 0.95),
                        percentile(sortedTotal, 0.99),
                    ),
                )
                if (sortedGpu.isNotEmpty()) {
                    appendLine(
                        String.format(
                            Locale.US,
                            "120Hz FrameMetrics GPU Duration: frames=%d avg=%.2fms p50=%.2fms p90=%.2fms p95=%.2fms p99=%.2fms",
                            sortedGpu.size,
                            sortedGpu.average(),
                            gpuP50,
                            percentile(sortedGpu, 0.90),
                            percentile(sortedGpu, 0.95),
                            percentile(sortedGpu, 0.99),
                        ),
                    )
                }
                appendLine("\n--- ACTIVE DISPLAY MODE DURING DRAG ---")
                appendLine(activeDisplaySnapshot)
                appendLine("\n--- DUMPSYS GFXINFO (com.zidee.ress) ---")
                appendLine(gfxInfoSummary)
            }

        Log.i(LOG_TAG, report)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        writeEvidenceText(context, "drag_120hz_profile.txt", report)
        assertTrue("Expected median VSync <= 9.5ms on 120Hz panel, actual p50=$vsyncP50", vsyncP50 <= 9.5)
        assertTrue(
            "Expected median pipelined total frame <= 11.93ms 120Hz presentation deadline, actual p50=$totalP50",
            totalP50 <= PIPELINE_DEADLINE_120HZ_MS,
        )
        if (sortedGpu.isNotEmpty()) {
            assertTrue("Expected median GPU duration <= 8.33ms 120Hz budget, actual p50=$gpuP50", gpuP50 <= BUDGET_120HZ_MS)
        }
    }

    companion object {
        private const val LOG_TAG = "Phase2Drag120Hz"
        private const val TARGET_REFRESH_HZ = 120f
        private const val WARMUP_FRAMES = 40
        private const val MEASURED_FRAMES = 240
        private const val BUDGET_120HZ_MS = 8.333
        private const val PIPELINE_DEADLINE_120HZ_MS = 11.933

        private fun captureActiveDisplayMode(): String =
            runShellCommand("dumpsys display")
                .lineSequence()
                .filter { line ->
                    line.contains("mActiveSfDisplayMode") ||
                        line.contains("renderFrameRate") ||
                        line.contains("refreshRateOverride")
                }
                .take(8)
                .joinToString("\n")

        private fun captureGfxInfoSummary(): String =
            runShellCommand("dumpsys gfxinfo com.zidee.ress")
                .lineSequence()
                .take(36)
                .joinToString("\n")

        private fun runShellCommand(command: String): String {
            val pfd: ParcelFileDescriptor =
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
        }

        private fun percentile(
            sorted: List<Double>,
            ratio: Double,
        ): Double {
            val idx = ((sorted.size - 1) * ratio).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[idx]
        }

        private fun writeEvidenceText(
            context: Context,
            fileName: String,
            text: String,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values =
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/zidee_phase2_evidence")
                    }
                val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            } else {
                val dir = File(requireNotNull(context.getExternalFilesDir(null)), "phase2_evidence")
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, fileName)).use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }
}

@Composable
private fun BenchmarkSplitHost(
    splitFraction: Float,
    onSplitChange: (Float) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.bgCanvas)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BeforeAfterSplitSlider(
            splitFraction = splitFraction,
            onSplitFractionChange = onSplitChange,
            bottomStatusText = "Kanvas 9:16 · Blur BG Radius: 35px",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(460.dp),
            beforeContent = {
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
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("[ ZIDEE RESS HD+ ]", style = typography.metricMono, color = colors.accentAmberBright)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("✓ Lanczos-3 1080×1920", style = typography.metricMono, color = colors.contrastWhite)
                        Text("✓ 30fps SDR Tajam", style = typography.metricMono, color = colors.statusSuccess)
                    }
                }
            },
        )
    }
}
