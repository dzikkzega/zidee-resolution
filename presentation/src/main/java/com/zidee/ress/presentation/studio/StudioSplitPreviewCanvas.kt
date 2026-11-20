package com.zidee.ress.presentation.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zidee.ress.core.format.MediaFormatters
import com.zidee.ress.designsystem.component.BeforeAfterSplitSlider
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.CropAdjustment
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.presentation.common.rememberMediaThumbnail
import com.zidee.ress.presentation.common.toCanvasStatusPill
import kotlin.math.roundToInt

/**
 * Interactive darkroom studio preview canvas wrapping [BeforeAfterSplitSlider]
 * with color-faithful source rendering, dynamic `Blur BG 9:16` simulation,
 * and pixel-accurate `Crop 9:16` pan/zoom derived directly from [CanvasGeometry.Crop].
 */
@Composable
fun StudioSplitPreviewCanvas(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    beforeLabel: String = "ASLI",
    afterLabel: String? = null,
    showBottomPill: Boolean = true,
    previewBitmapOverride: ImageBitmap? = null,
    renderedAfterBitmap: ImageBitmap? = null,
) {
    val activeEntry = state.activeEntry
    val loadedThumbnail =
        if (previewBitmapOverride == null) {
            rememberMediaThumbnail(
                uri = activeEntry?.uri?.value,
                mediaType = activeEntry?.metadata?.mediaType,
                maxDimensionPx = 960,
            )
        } else {
            null
        }
    val previewBitmap = previewBitmapOverride ?: loadedThumbnail
    val resolvedAfterLabel =
        afterLabel ?: if (renderedAfterBitmap != null) "HASIL NYATA HD+" else "SIMULASI PRATINJAU"

    var isCropGestureActive by remember(state.selectedAspectMode) {
        mutableStateOf(false)
    }
    val bottomStatus =
        if (showBottomPill) {
            state.activeSpec.toCanvasStatusPill(state.selectedAspectMode, state.blurRadiusPx)
        } else {
            null
        }

    Box(modifier = modifier.testTag(StudioPreviewTags.ROOT)) {
        BeforeAfterSplitSlider(
            splitFraction = state.splitFraction,
            onSplitFractionChange = { onEvent(StudioEditorUiEvent.UpdateSplitFraction(it)) },
            beforeLabel = beforeLabel,
            afterLabel = resolvedAfterLabel,
            bottomStatusText = bottomStatus,
            modifier = Modifier.fillMaxSize(),
            beforeContent = {
                PreviewStageSide(
                    bitmap = previewBitmap,
                    renderedAfterBitmap = null,
                    aspectMode = state.selectedAspectMode,
                    crop = state.cropAdjustment,
                    isEnhancedSide = false,
                    state = state,
                )
            },
            afterContent = {
                PreviewStageSide(
                    bitmap = previewBitmap,
                    renderedAfterBitmap = renderedAfterBitmap,
                    aspectMode = state.selectedAspectMode,
                    crop = state.cropAdjustment,
                    isEnhancedSide = true,
                    state = state,
                )
            },
        )

        if (state.selectedAspectMode == CanvasAspectMode.CROP_9_16) {
            val bitmapSize = previewBitmap?.let { PixelSize(it.width, it.height) }
            CropInteractionOverlay(
                isCropGestureActive = isCropGestureActive,
                crop = state.cropAdjustment,
                activeSpec = state.activeSpec,
                bitmapSize = bitmapSize,
                onToggleCropGesture = { isCropGestureActive = !isCropGestureActive },
                onUpdateCrop = { onEvent(StudioEditorUiEvent.UpdateCrop(it)) },
                onResetCrop = { onEvent(StudioEditorUiEvent.UpdateCrop(CropAdjustment.CENTER)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PreviewStageSide(
    bitmap: ImageBitmap?,
    renderedAfterBitmap: ImageBitmap?,
    aspectMode: CanvasAspectMode,
    crop: CropAdjustment,
    isEnhancedSide: Boolean,
    state: StudioEditorUiState,
) {
    val colors = ZideeTheme.colors
    val stageTag = if (isEnhancedSide) StudioPreviewTags.AFTER_STAGE else StudioPreviewTags.BEFORE_STAGE
    if (bitmap == null && renderedAfterBitmap == null) {
        OpticalComparisonPlaceholder(
            isEnhancedSide = isEnhancedSide,
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(stageTag)
                    .background(if (isEnhancedSide) colors.bgSurface else colors.bgElevated),
        )
        return
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(stageTag)
                .clipToBounds()
                .background(colors.bgCanvas),
        contentAlignment = Alignment.Center,
    ) {
        when (aspectMode) {
            CanvasAspectMode.CROP_9_16 -> {
                Crop916PreviewCanvas(
                    sourceBitmap = bitmap,
                    renderedAfterBitmap = if (isEnhancedSide) renderedAfterBitmap else null,
                    activeSpec = state.activeSpec,
                    crop = crop,
                    isEnhancedSide = isEnhancedSide,
                )
            }
            CanvasAspectMode.BLUR_BG_9_16 -> {
                BlurBgPreviewContent(
                    sourceBitmap = bitmap,
                    renderedAfterBitmap = if (isEnhancedSide) renderedAfterBitmap else null,
                    blurRadiusPx = state.blurRadiusPx,
                    isEnhancedSide = isEnhancedSide,
                )
            }
            CanvasAspectMode.ORIGINAL -> {
                val displayBitmap = if (isEnhancedSide && renderedAfterBitmap != null) renderedAfterBitmap else bitmap
                if (displayBitmap != null) {
                    Image(
                        bitmap = displayBitmap,
                        contentDescription = sideContentDescription(isEnhancedSide, renderedAfterBitmap != null),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Crop916PreviewCanvas(
    sourceBitmap: ImageBitmap?,
    renderedAfterBitmap: ImageBitmap?,
    activeSpec: OptimalMediaSpec?,
    crop: CropAdjustment,
    isEnhancedSide: Boolean,
) {
    val canvasTag = if (isEnhancedSide) StudioPreviewTags.CROP_CANVAS_AFTER else StudioPreviewTags.CROP_CANVAS_BEFORE
    Canvas(modifier = Modifier.fillMaxSize().testTag(canvasTag)) {
        val containerW = size.width.roundToInt().coerceAtLeast(1)
        val containerH = size.height.roundToInt().coerceAtLeast(1)
        if (renderedAfterBitmap != null) {
            val outputSize = activeSpec?.outputSize ?: PixelSize(renderedAfterBitmap.width, renderedAfterBitmap.height)
            val dst = CropPreviewGeometry.fitAspectInContainer(containerW, containerH, outputSize)
            drawImage(
                image = renderedAfterBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(renderedAfterBitmap.width, renderedAfterBitmap.height),
                dstOffset = IntOffset(dst.left, dst.top),
                dstSize = IntSize(dst.width, dst.height),
                filterQuality = FilterQuality.High,
            )
        } else if (sourceBitmap != null) {
            val rects =
                CropPreviewGeometry.resolveCropDrawRects(
                    bitmapSize = PixelSize(sourceBitmap.width, sourceBitmap.height),
                    containerWidthPx = containerW,
                    containerHeightPx = containerH,
                    activeSpec = activeSpec,
                    cropAdjustment = crop,
                )
            drawImage(
                image = sourceBitmap,
                srcOffset = IntOffset(rects.srcLeft, rects.srcTop),
                srcSize = IntSize(rects.srcWidth, rects.srcHeight),
                dstOffset = IntOffset(rects.dstLeft, rects.dstTop),
                dstSize = IntSize(rects.dstWidth, rects.dstHeight),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

@Composable
private fun BlurBgPreviewContent(
    sourceBitmap: ImageBitmap?,
    renderedAfterBitmap: ImageBitmap?,
    blurRadiusPx: Int,
    isEnhancedSide: Boolean,
) {
    if (isEnhancedSide && renderedAfterBitmap != null) {
        Image(
            bitmap = renderedAfterBitmap,
            contentDescription = "Hasil Nyata Zidee HD+",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    if (sourceBitmap == null) return

    if (isEnhancedSide) {
        val previewBlurDp = (blurRadiusPx * 0.70f).dp
        Image(
            bitmap = sourceBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(radius = previewBlurDp)
                    .alpha(0.55f),
        )
    }
    Image(
        bitmap = sourceBitmap,
        contentDescription = sideContentDescription(isEnhancedSide, isRealOutput = false),
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun sideContentDescription(
    isEnhancedSide: Boolean,
    isRealOutput: Boolean,
): String =
    when {
        !isEnhancedSide -> "Pratinjau Asli"
        isRealOutput -> "Hasil Nyata Zidee HD+"
        else -> "Simulasi Pratinjau Zidee HD+"
    }

@Composable
private fun OpticalComparisonPlaceholder(
    isEnhancedSide: Boolean,
    state: StudioEditorUiState,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val targetRes =
        state.activeSpec?.let {
            MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height)
        } ?: "1080×1920"
    val sharpness = state.photoConfig.sharpnessPercent

    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = if (isEnhancedSide) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isEnhancedSide) {
                Text(
                    text = "[ SIMULASI TARGET HD+ ]",
                    style = typography.metricMono,
                    color = colors.accentAmberBright,
                    fontWeight = FontWeight.Bold,
                )
                Text("✓ Lanczos-3 $targetRes", style = typography.metricMono, color = colors.contrastWhite)
                Text("✓ Unsharp Mask +$sharpness%", style = typography.metricMono, color = colors.contrastWhite)
                Text("✓ Output Nyata Saat Ekspor", style = typography.metricMono, color = colors.statusSuccess)
            } else {
                Text(
                    text = "[ INPUT ASLI ]",
                    style = typography.metricMono,
                    color = colors.textMuted,
                    fontWeight = FontWeight.Bold,
                )
                Text("• Warna sumber dipertahankan", style = typography.metricMono, color = colors.textSecondary)
                Text("• Tanpa filter buatan di sisi Asli", style = typography.metricMono, color = colors.textSecondary)
                Text("• Muat media untuk pratinjau", style = typography.metricMono, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun CropInteractionOverlay(
    isCropGestureActive: Boolean,
    crop: CropAdjustment,
    activeSpec: OptimalMediaSpec?,
    bitmapSize: PixelSize?,
    onToggleCropGesture: () -> Unit,
    onUpdateCrop: (CropAdjustment) -> Unit,
    onResetCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestCrop by rememberUpdatedState(crop)
    val latestSpec by rememberUpdatedState(activeSpec)
    val latestBitmapSize by rememberUpdatedState(bitmapSize)
    val latestOnUpdate by rememberUpdatedState(onUpdateCrop)

    Box(modifier = modifier) {
        if (isCropGestureActive) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val nextZoom =
                                    (latestCrop.zoom * zoomChange)
                                        .coerceIn(1.0, CropAdjustment.MAX_ZOOM)
                                val refSize = latestSpec?.sourceSize ?: latestBitmapSize ?: PixelSize(1080, 1920)
                                val rects =
                                    CropPreviewGeometry.resolveCropDrawRects(
                                        bitmapSize = refSize,
                                        containerWidthPx = size.width.coerceAtLeast(1),
                                        containerHeightPx = size.height.coerceAtLeast(1),
                                        activeSpec = latestSpec,
                                        cropAdjustment = latestCrop.copy(zoom = nextZoom),
                                    )
                                val deltaX =
                                    CropPreviewGeometry.computePanDelta(
                                        dragDeltaPx = pan.x,
                                        viewportSpanPx = rects.dstWidth.toFloat(),
                                        sourceSpanPx = refSize.width,
                                        cropSpanPx = rects.srcWidth,
                                    )
                                val deltaY =
                                    CropPreviewGeometry.computePanDelta(
                                        dragDeltaPx = pan.y,
                                        viewportSpanPx = rects.dstHeight.toFloat(),
                                        sourceSpanPx = refSize.height,
                                        cropSpanPx = rects.srcHeight,
                                    )
                                val nextPanX = (latestCrop.panX + deltaX).coerceIn(0.0, 1.0)
                                val nextPanY = (latestCrop.panY + deltaY).coerceIn(0.0, 1.0)
                                latestOnUpdate(CropAdjustment(panX = nextPanX, panY = nextPanY, zoom = nextZoom))
                            }
                        },
            ) {
                RuleOfThirdsGridOverlay(
                    outputSize = activeSpec?.outputSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        CropToggleControlsRow(
            isCropGestureActive = isCropGestureActive,
            hasCustomCrop = crop != CropAdjustment.CENTER,
            onToggleCropGesture = onToggleCropGesture,
            onResetCrop = onResetCrop,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp),
        )
    }
}

@Composable
private fun CropToggleControlsRow(
    isCropGestureActive: Boolean,
    hasCustomCrop: Boolean,
    onToggleCropGesture: () -> Unit,
    onResetCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.clickable(onClick = onToggleCropGesture),
            shape = RoundedCornerShape(16.dp),
            color = if (isCropGestureActive) colors.accentAmber else colors.bgGlassDark,
            border = BorderStroke(1.dp, if (isCropGestureActive) colors.accentAmber else colors.borderActive),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Crop,
                    contentDescription = null,
                    tint = if (isCropGestureActive) colors.onAccent else colors.accentAmberBright,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = if (isCropGestureActive) "Geser Crop Aktif · Selesai" else "Atur Posisi Crop 9:16",
                    style = typography.metricMono,
                    color = if (isCropGestureActive) colors.onAccent else colors.contrastWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (hasCustomCrop) {
            Surface(
                modifier = Modifier.clickable(onClick = onResetCrop),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgGlassDark,
                border = BorderStroke(1.dp, colors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset Crop",
                        tint = colors.accentAmberBright,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Reset",
                        style = typography.metricMono,
                        color = colors.accentAmberBright,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleOfThirdsGridOverlay(
    outputSize: PixelSize?,
    modifier: Modifier = Modifier,
) {
    val lineColor = ZideeTheme.colors.accentAmberBright.copy(alpha = 0.45f)
    Canvas(modifier = modifier) {
        val dst =
            CropPreviewGeometry.fitAspectInContainer(
                containerWidthPx = size.width.roundToInt().coerceAtLeast(1),
                containerHeightPx = size.height.roundToInt().coerceAtLeast(1),
                outputSize = outputSize ?: PixelSize(1080, 1920),
            )
        val left = dst.left.toFloat()
        val top = dst.top.toFloat()
        val w = dst.width.toFloat()
        val h = dst.height.toFloat()
        val stroke = 1.dp.toPx()
        for (i in 1..2) {
            val x = left + w * (i / 3f)
            drawLine(color = lineColor, start = Offset(x, top), end = Offset(x, top + h), strokeWidth = stroke)
            val y = top + h * (i / 3f)
            drawLine(color = lineColor, start = Offset(left, y), end = Offset(left + w, y), strokeWidth = stroke)
        }
    }
}
