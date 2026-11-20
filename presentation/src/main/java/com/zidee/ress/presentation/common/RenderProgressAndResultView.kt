package com.zidee.ress.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zidee.ress.core.format.MediaFormatters
import com.zidee.ress.designsystem.component.zideeTactileScale
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.ZideeAlbum
import kotlin.math.roundToInt

/**
 * Progress and Auto-Split result screen matching FigCraft `HP 05 · Render Progress & Auto-Split Result`.
 */
@Composable
fun RenderProgressAndResultView(
    isRunning: Boolean,
    progressFraction: Float,
    engineLabel: String,
    specDetailLabel: String,
    stageLabel: String,
    targetPathLabel: String,
    completedItems: List<OptimizedMediaItem>,
    platform: TargetPlatform,
    onCancel: () -> Unit,
    onShareItem: (OptimizedMediaItem) -> Unit,
    onShareAll: (List<OptimizedMediaItem>) -> Unit,
    onDoneNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    doneButtonLabel: String = "Lihat di Album ZideeResolution / Studio Utama",
    partialFailureMessage: String? = null,
    onDismissPartialError: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RenderStatusHeader()

        if (!partialFailureMessage.isNullOrBlank()) {
            ErrorAlertBanner(
                message = partialFailureMessage,
                onDismiss = onDismissPartialError ?: {},
            )
        }

        if (isRunning) {
            ActiveRenderProgressCard(
                progressFraction = progressFraction,
                engineLabel = engineLabel,
                specDetailLabel = specDetailLabel,
                stageLabel = stageLabel,
                targetPathLabel = targetPathLabel,
                onCancel = onCancel,
            )
        }

        if (completedItems.isNotEmpty()) {
            CompletedOptimizationSection(
                items = completedItems,
                platform = platform,
                doneButtonLabel = doneButtonLabel,
                isPartial = !partialFailureMessage.isNullOrBlank(),
                onShareItem = onShareItem,
                onShareAll = onShareAll,
                onDoneNavigate = onDoneNavigate,
            )
        }
    }
}

@Composable
private fun RenderStatusHeader() {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Proses & Hasil Optimasi",
            style = typography.sectionHeader,
            color = colors.contrastWhite,
        )
        Text(
            text = "FOREGROUND SERVICE · ON-DEVICE",
            style = typography.metricBadge,
            color = colors.accentAmber,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        text = "Aman ditinggal ke app lain · Tidak dibunuh sistem Android",
        style = typography.caption,
        color = colors.textSecondary,
    )
}

@Composable
private fun ActiveRenderProgressCard(
    progressFraction: Float,
    engineLabel: String,
    specDetailLabel: String,
    stageLabel: String,
    targetPathLabel: String,
    onCancel: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val shapes = ZideeTheme.shapes
    val clamped = progressFraction.coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.card,
        color = colors.bgSurface,
        border = BorderStroke(1.5.dp, colors.accentAmber.copy(alpha = 0.48f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProgressMetricHeader(
                percent = (clamped * 100f).roundToInt(),
                engineLabel = engineLabel,
                specDetailLabel = specDetailLabel,
            )
            ProgressTrackBar(fraction = clamped)
            ProgressStageDetails(stageLabel = stageLabel, targetPathLabel = targetPathLabel)
            CancelRenderButton(onCancel = onCancel)
        }
    }
}

@Composable
private fun ProgressMetricHeader(
    percent: Int,
    engineLabel: String,
    specDetailLabel: String,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Text(
        text = "● $engineLabel",
        style = typography.metricBadge,
        color = colors.accentAmber,
        fontSize = 10.sp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "$percent%",
            style = typography.metricMono,
            color = colors.contrastWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp,
        )
        Text(
            text = specDetailLabel,
            style = typography.metricBadge,
            color = colors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressTrackBar(fraction: Float) {
    val colors = ZideeTheme.colors
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.bgCanvas),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(colors.accentAmber),
        )
    }
}

@Composable
private fun ProgressStageDetails(
    stageLabel: String,
    targetPathLabel: String,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Text(
        text = stageLabel,
        style = typography.metricBadge,
        color = colors.contrastWhite,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = targetPathLabel,
        style = typography.metricBadge,
        color = colors.textSecondary,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CancelRenderButton(onCancel: () -> Unit) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    OutlinedButton(
        onClick = onCancel,
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ZideeMinTouchTarget),
        shape = shapes.controlInner,
        border = BorderStroke(1.dp, colors.statusDanger.copy(alpha = 0.45f)),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.bgElevated,
                contentColor = colors.statusDanger,
            ),
    ) {
        Text(
            text = "Batalkan Proses Render",
            style = typography.controlLabelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CompletedOptimizationSection(
    items: List<OptimizedMediaItem>,
    platform: TargetPlatform,
    doneButtonLabel: String,
    isPartial: Boolean,
    onShareItem: (OptimizedMediaItem) -> Unit,
    onShareAll: (List<OptimizedMediaItem>) -> Unit,
    onDoneNavigate: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val primaryType = items.first().mediaType
    val relativePath = ZideeAlbum.relativePathFor(primaryType)

    SuccessGalleryBanner(relativePath = relativePath, fileCount = items.size, isPartial = isPartial)

    if (items.size > 1) {
        Text(
            text = "HASIL OPTIMASI & AUTO-SPLIT (${platform.toFullTitle().uppercase()}):",
            style = typography.metricBadge,
            color = colors.textSecondary,
            fontSize = 10.sp,
        )
    }

    items.forEachIndexed { idx, item ->
        SplitOrBatchPartCard(
            item = item,
            highlight = idx == 0,
            onShare = { onShareItem(item) },
        )
    }

    StoryUploadTipCard()
    CompletedActionButtons(
        itemCount = items.size,
        platform = platform,
        doneButtonLabel = doneButtonLabel,
        onShareAll = { onShareAll(items) },
        onDoneNavigate = onDoneNavigate,
    )
}

@Composable
private fun StoryUploadTipCard() {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.control,
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Text(
            text = "Tips: Jangan zoom atau putar media di dalam WA/IG Story agar kanvas presisi tidak dirender ulang.",
            style = typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CompletedActionButtons(
    itemCount: Int,
    platform: TargetPlatform,
    doneButtonLabel: String,
    onShareAll: () -> Unit,
    onDoneNavigate: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }
    val shareAllLabel =
        if (itemCount > 1) {
            "Kirim Semua ($itemCount File) ke ${platform.toShortLabel()}"
        } else {
            "Kirim ke ${platform.toShortLabel()}"
        }

    Button(
        onClick = onShareAll,
        interactionSource = interactionSource,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .zideeTactileScale(interactionSource = interactionSource),
        shape = shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.accentAmber,
                contentColor = colors.onAccent,
            ),
    ) {
        Text(
            text = shareAllLabel,
            style = typography.controlLabel,
            fontWeight = FontWeight.Bold,
        )
    }

    OutlinedButton(
        onClick = onDoneNavigate,
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ZideeMinTouchTarget),
        shape = shapes.control,
        border = BorderStroke(1.dp, colors.borderSubtle),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.bgElevated,
                contentColor = colors.contrastWhite,
            ),
    ) {
        Text(
            text = doneButtonLabel,
            style = typography.controlLabelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SuccessGalleryBanner(
    relativePath: String,
    fileCount: Int,
    isPartial: Boolean,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val title = if (isPartial) "⚠ Tersimpan Sebagian di Galeri" else "✓ Selesai & Tersimpan Permanen!"
    val accent = if (isPartial) colors.accentAmber else colors.statusSuccess
    val containerColor = if (isPartial) colors.accentAmberSoft else colors.statusSuccessSoft
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = typography.controlLabel,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Galeri / $relativePath ($fileCount File)",
                style = typography.metricBadge,
                color = colors.contrastWhite,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun SplitOrBatchPartCard(
    item: OptimizedMediaItem,
    highlight: Boolean,
    onShare: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val borderColor = if (highlight) colors.accentAmber.copy(alpha = 0.35f) else colors.borderSubtle

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PartIndexBadge(partIndex = item.partIndex, highlight = highlight)
            PartMetricsColumn(item = item, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(4.dp))
            PartShareButton(highlight = highlight, onShare = onShare)
        }
    }
}

@Composable
private fun PartIndexBadge(
    partIndex: Int,
    highlight: Boolean,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = Modifier.size(width = 48.dp, height = 64.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.bgElevated,
        border = BorderStroke(1.dp, colors.borderCanvasInset),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "P$partIndex",
                style = typography.metricBadge,
                color = if (highlight) colors.accentAmberBright else colors.contrastWhite,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun PartMetricsColumn(
    item: OptimizedMediaItem,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val partTitle =
        if (item.totalParts > 1) {
            "Part ${item.partIndex} / ${item.totalParts} · ${item.displayName}"
        } else {
            item.displayName
        }
    val metricsLine =
        buildString {
            append(MediaFormatters.formatResolution(item.outputSize.width, item.outputSize.height))
            item.durationMs?.let { append(" · ${MediaFormatters.formatDuration(it)}") }
            append(" · ${MediaFormatters.formatBytes(item.outputSizeBytes)}")
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = partTitle,
            style = typography.controlLabelSmall,
            color = colors.contrastWhite,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metricsLine,
            style = typography.metricBadge,
            color = colors.textSecondary,
            fontSize = 10.sp,
        )
        Text(
            text = "✓ sRGB SDR · ${item.aspectMode.toShortLabel()}",
            style = typography.metricBadge,
            color = colors.statusSuccess,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun PartShareButton(
    highlight: Boolean,
    onShare: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    OutlinedButton(
        onClick = onShare,
        modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = ZideeMinTouchTarget),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, if (highlight) colors.borderActive else colors.borderSubtle),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.bgElevated,
                contentColor = if (highlight) colors.accentAmberBright else colors.contrastWhite,
            ),
    ) {
        Text(
            text = "Kirim ↗",
            style = typography.controlLabelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 360)
@Composable
private fun RenderProgressAndResultPreview() {
    val sampleItems =
        listOf(
            OptimizedMediaItem(
                id = "1",
                mediaStoreUri = MediaUri("content://media/external/video/media/1"),
                displayName = "ZIDEE_VID_0942_WA_P1.mp4",
                mediaType = MediaType.VIDEO,
                platform = TargetPlatform.WA_STORY,
                aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                originalSize = PixelSize(3840, 2160),
                outputSize = PixelSize(1080, 1920),
                originalSizeBytes = 58_000_000L,
                outputSizeBytes = 4_800_000L,
                durationMs = 30_000L,
                partIndex = 1,
                totalParts = 2,
                createdAtEpochMs = 1L,
            ),
            OptimizedMediaItem(
                id = "2",
                mediaStoreUri = MediaUri("content://media/external/video/media/2"),
                displayName = "ZIDEE_VID_0942_WA_P2.mp4",
                mediaType = MediaType.VIDEO,
                platform = TargetPlatform.WA_STORY,
                aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                originalSize = PixelSize(3840, 2160),
                outputSize = PixelSize(1080, 1920),
                originalSizeBytes = 58_000_000L,
                outputSizeBytes = 2_500_000L,
                durationMs = 15_000L,
                partIndex = 2,
                totalParts = 2,
                createdAtEpochMs = 2L,
            ),
        )
    ZideeTheme {
        RenderProgressAndResultView(
            isRunning = true,
            progressFraction = 0.78f,
            engineLabel = "MERENDER OFFLINE (CPU libx264)",
            specDetailLabel = "Item 1/1 · 1080×1920 30fps",
            stageLabel = "Tahap: Filtergraph Lanczos-3 + Unsharp + Blur BG",
            targetPathLabel = "Target: Movies/ZideeResolution/ZIDEE_VID_0942...",
            completedItems = sampleItems,
            platform = TargetPlatform.WA_STORY,
            onCancel = {},
            onShareItem = {},
            onShareAll = {},
            onDoneNavigate = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
