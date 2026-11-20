package com.zidee.ress.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme
import kotlin.math.roundToInt

/**
 * Interactive Before/After split-screen comparison canvas with 120Hz GPU clip & translation rendering
 * (deferring fraction reads into the Draw phase via `clipRect` and `graphicsLayer`),
 * center magnetic detent with haptic feedback, and inset darkroom frame outline.
 */
@Composable
fun BeforeAfterSplitSlider(
    splitFraction: Float,
    onSplitFractionChange: (Float) -> Unit,
    beforeContent: @Composable BoxScope.() -> Unit,
    afterContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    beforeLabel: String = "SEBELUM",
    afterLabel: String = "ZIDEE HD+",
    bottomStatusText: String? = null,
    showLabels: Boolean = true,
) {
    val colors = ZideeTheme.colors
    val shapes = ZideeTheme.shapes
    val density = LocalDensity.current
    val clampedFraction = SplitSliderMath.clampFraction(splitFraction)
    val fractionState = rememberUpdatedState(clampedFraction)
    val fractionProvider = remember { { fractionState.value } }

    BoxWithConstraints(
        modifier =
            modifier
                .testTag(SplitSliderSpec.TAG_ROOT)
                .clip(shapes.canvasFrame)
                .background(colors.bgCanvas)
                .splitComparisonSemantics(
                    fractionState = fractionState,
                    onSplitFractionChange = onSplitFractionChange,
                ),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val handleHitRadiusPx = with(density) { (ZideeMinTouchTarget / 2).toPx() }

        SplitInteractiveSurface(
            fractionProvider = fractionProvider,
            widthPx = widthPx,
            handleHitRadiusPx = handleHitRadiusPx,
            onSplitFractionChange = onSplitFractionChange,
            beforeContent = beforeContent,
            afterContent = afterContent,
            beforeLabel = beforeLabel,
            afterLabel = afterLabel,
            bottomStatusText = bottomStatusText,
            showLabels = showLabels,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(1.dp, colors.borderCanvasInset, shapes.canvasFrame),
        )
    }
}

private fun Modifier.splitComparisonSemantics(
    fractionState: State<Float>,
    onSplitFractionChange: (Float) -> Unit,
): Modifier =
    semantics {
        val current = fractionState.value
        contentDescription = "Pembanding sebelum dan sesudah Zidee Ress"
        stateDescription = "${(current * 100f).roundToInt()}%"
        progressBarRangeInfo = ProgressBarRangeInfo(current, 0f..1f)
        setProgress { targetValue ->
            onSplitFractionChange(SplitSliderMath.clampFraction(targetValue))
            true
        }
    }

@Composable
private fun SplitInteractiveSurface(
    fractionProvider: () -> Float,
    widthPx: Float,
    handleHitRadiusPx: Float,
    onSplitFractionChange: (Float) -> Unit,
    beforeContent: @Composable BoxScope.() -> Unit,
    afterContent: @Composable BoxScope.() -> Unit,
    beforeLabel: String,
    afterLabel: String,
    bottomStatusText: String?,
    showLabels: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val latestOnFractionChange by rememberUpdatedState(onSplitFractionChange)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        val previous = fractionProvider()
                        val next =
                            SplitSliderMath.applyCenterDetent(
                                SplitSliderMath.fractionFromPointerX(offset.x, widthPx),
                            )
                        if (SplitSliderMath.didCrossCenter(previous, next)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestOnFractionChange(next)
                    }
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val previous = fractionProvider()
                        val raw = SplitSliderMath.fractionFromPointerX(change.position.x, widthPx)
                        val snapped = SplitSliderMath.applyCenterDetent(raw)
                        if (SplitSliderMath.didCrossCenter(previous, snapped)) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestOnFractionChange(snapped)
                    }
                },
    ) {
        Box(modifier = Modifier.fillMaxSize(), content = afterContent)

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * fractionProvider()) {
                            this@drawWithContent.drawContent()
                        }
                    },
            content = beforeContent,
        )

        SplitDividerOverlay(
            fractionProvider = fractionProvider,
            widthPx = widthPx,
            handleHitRadiusPx = handleHitRadiusPx,
        )

        if (showLabels) {
            SplitFloatingBadges(
                beforeLabel = beforeLabel,
                afterLabel = afterLabel,
                bottomStatusText = bottomStatusText,
            )
        }
    }
}

@Composable
private fun BoxScope.SplitDividerOverlay(
    fractionProvider: () -> Float,
    widthPx: Float,
    handleHitRadiusPx: Float,
) {
    val colors = ZideeTheme.colors
    val density = LocalDensity.current
    val halfHaloPx = with(density) { 2.dp.toPx() }
    val halfLinePx = with(density) { (SplitSliderSpec.DividerLineWidth / 2).toPx() }

    Box(
        modifier =
            Modifier
                .graphicsLayer { translationX = widthPx * fractionProvider() - halfHaloPx }
                .width(4.dp)
                .fillMaxHeight()
                .background(colors.bgCanvas.copy(alpha = 0.55f)),
    )

    Box(
        modifier =
            Modifier
                .testTag(SplitSliderSpec.TAG_DIVIDER_LINE)
                .graphicsLayer { translationX = widthPx * fractionProvider() - halfLinePx }
                .width(SplitSliderSpec.DividerLineWidth)
                .fillMaxHeight()
                .background(colors.contrastWhite),
    )

    Box(
        modifier =
            Modifier
                .testTag(SplitSliderSpec.TAG_HANDLE_HIT_AREA)
                .align(Alignment.CenterStart)
                .graphicsLayer { translationX = widthPx * fractionProvider() - handleHitRadiusPx }
                .size(ZideeMinTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .testTag(SplitSliderSpec.TAG_HANDLE_VISUAL)
                    .size(SplitSliderSpec.HandleVisualSize)
                    .shadow(elevation = 6.dp, shape = CircleShape),
            shape = CircleShape,
            color = colors.accentAmber,
            border = BorderStroke(2.dp, colors.contrastWhite),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(16.dp),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SplitFloatingBadges(
    beforeLabel: String,
    afterLabel: String,
    bottomStatusText: String?,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes

    Surface(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        shape = shapes.badge,
        color = colors.bgGlassDark,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Text(
            text = "◄ $beforeLabel",
            style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }

    Surface(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        shape = shapes.badge,
        color = colors.bgGlassDark,
        border = BorderStroke(1.dp, colors.borderActive),
    ) {
        Text(
            text = "$afterLabel ►",
            style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
            color = colors.accentAmberBright,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }

    if (!bottomStatusText.isNullOrBlank()) {
        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            shape = shapes.pill,
            color = colors.bgGlassDark,
            border = BorderStroke(1.dp, colors.borderSubtle),
        ) {
            Text(
                text = bottomStatusText,
                style = typography.metricMono,
                color = colors.contrastWhite,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10)
@Composable
private fun BeforeAfterSplitSliderPreview() {
    var split by remember { mutableFloatStateOf(0.5f) }
    ZideeTheme {
        val colors = ZideeTheme.colors
        val typography = ZideeTheme.typography
        BeforeAfterSplitSlider(
            splitFraction = split,
            onSplitFractionChange = { split = it },
            bottomStatusText = "Kanvas 9:16 · Blur BG Radius: 35px",
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(340.dp),
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("[ ZIDEE RESS HD+ ]", style = typography.metricMono, color = colors.accentAmberBright)
                        Text("✓ Lanczos-3 1080×1920", style = typography.metricMono, color = colors.contrastWhite)
                        Text("✓ 30fps SDR Tajam", style = typography.metricMono, color = colors.statusSuccess)
                    }
                }
            },
        )
    }
}
