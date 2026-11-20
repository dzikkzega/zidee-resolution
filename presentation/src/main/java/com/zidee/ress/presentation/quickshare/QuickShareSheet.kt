package com.zidee.ress.presentation.quickshare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zidee.ress.core.format.MediaFormatters
import com.zidee.ress.designsystem.component.AdvancedParameterPanel
import com.zidee.ress.designsystem.component.CanvasModeSegmentedControl
import com.zidee.ress.designsystem.component.PlatformPresetSelector
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PhotoEncodingSpec
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.ScalingAlgorithm
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.model.VideoEncodingSpec
import com.zidee.ress.domain.model.ZideeAlbum
import com.zidee.ress.presentation.common.ErrorAlertBanner
import com.zidee.ress.presentation.common.OfflineEnginePill
import com.zidee.ress.presentation.common.RenderProgressAndResultView
import com.zidee.ress.presentation.common.ZideePresentationDefaults
import com.zidee.ress.presentation.common.buildAdvancedTuningUiState
import com.zidee.ress.presentation.common.dispatchAdvancedTuningEvent
import com.zidee.ress.presentation.common.rememberMediaThumbnail
import com.zidee.ress.presentation.common.resolveUriDisplayName
import com.zidee.ress.presentation.common.toActionTargetShort
import com.zidee.ress.presentation.common.toAsliSummaryLine
import com.zidee.ress.presentation.common.toDestinationAlbumHint
import com.zidee.ress.presentation.common.toDirectPackageHint
import com.zidee.ress.presentation.common.toTargetSummaryLine
import com.zidee.ress.presentation.studio.StudioDualActionRow

/**
 * Translucent Bottom Sheet matching FigCraft `HP 04 · Quick Share Bottom Sheet (Direct Share)`
 * and transitioning inline to `HP 05` during rendering and completion.
 */
@Composable
fun QuickShareSheet(
    state: QuickShareUiState,
    onEvent: (QuickShareUiEvent) -> Unit,
    onOpenFullStudio: () -> Unit,
    onShareCompletedItems: (List<OptimizedMediaItem>, TargetPlatform) -> Unit,
    onDismissSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val scrimInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bgCanvas.copy(alpha = 0.78f))
                .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismissSheet),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .clickable(interactionSource = sheetInteraction, indication = null, onClick = {}),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.5.dp, colors.accentAmber.copy(alpha = 0.40f)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickShareSheetHandleAndHeader()

                if (!state.errorMessage.isNullOrBlank()) {
                    ErrorAlertBanner(
                        message = state.errorMessage,
                        onDismiss = { onEvent(QuickShareUiEvent.DismissError) },
                    )
                }

                if (state.isProcessing || state.completedItems.isNotEmpty()) {
                    QuickShareProcessingBody(
                        state = state,
                        onEvent = onEvent,
                        onShareCompletedItems = onShareCompletedItems,
                        onDismissSheet = onDismissSheet,
                    )
                } else {
                    QuickShareControlBody(
                        state = state,
                        onEvent = onEvent,
                        onOpenFullStudio = onOpenFullStudio,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickShareSheetHandleAndHeader() {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.textMuted),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Zidee Ress · Quick Optimize",
            style = typography.sectionHeader,
            color = colors.contrastWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        OfflineEnginePill(text = "⚡ 100% OFFLINE", isAmber = true)
    }
}

@Composable
private fun QuickShareControlBody(
    state: QuickShareUiState,
    onEvent: (QuickShareUiEvent) -> Unit,
    onOpenFullStudio: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val activeType = state.inspectedSources.firstOrNull()?.mediaType
    val tuningUiState =
        buildAdvancedTuningUiState(
            mediaType = activeType,
            platform = state.selectedPlatform,
            photoConfig = state.photoConfig,
            videoConfig = state.videoConfig,
            activeSpec = state.activeSpec,
        )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickShareMediaPreviewCard(state = state)

        Text(
            text = "1. MAU KIRIM KE MANA?",
            style = typography.metricMono,
            color = colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        PlatformPresetSelector(
            options = ZideePresentationDefaults.platformOptions,
            selectedId = state.selectedPlatform.name,
            onSelect = { rawId ->
                TargetPlatform.entries.firstOrNull { it.name == rawId }?.let {
                    onEvent(QuickShareUiEvent.SelectPlatform(it))
                }
            },
        )

        Text(
            text = "2. MODE KANVAS & RASIO STORY",
            style = typography.metricMono,
            color = colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        CanvasModeSegmentedControl(
            options = ZideePresentationDefaults.canvasModeOptions,
            selectedId = state.selectedAspectMode.name,
            onSelect = { rawId ->
                CanvasAspectMode.entries.firstOrNull { it.name == rawId }?.let {
                    onEvent(QuickShareUiEvent.SelectAspectMode(it))
                }
            },
        )

        AdvancedParameterPanel(
            expanded = state.isAdvancedExpanded,
            onToggleExpand = { onEvent(QuickShareUiEvent.ToggleAdvanced) },
            summaryBadge = "Optimal (USM ${state.photoConfig.sharpnessPercent}%)",
            state = tuningUiState,
            onEvent = { tuningEvent ->
                dispatchAdvancedTuningEvent(
                    event = tuningEvent,
                    platform = state.selectedPlatform,
                    onUpdatePhoto = { t -> onEvent(QuickShareUiEvent.UpdatePhotoConfig(t)) },
                    onUpdateVideo = { t -> onEvent(QuickShareUiEvent.UpdateVideoConfig(t)) },
                )
            },
        )

        QuickShareFooterActions(
            state = state,
            onEvent = onEvent,
            onOpenFullStudio = onOpenFullStudio,
        )
    }
}

@Composable
private fun QuickShareFooterActions(
    state: QuickShareUiState,
    onEvent: (QuickShareUiEvent) -> Unit,
    onOpenFullStudio: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes

    OutlinedButton(
        onClick = onOpenFullStudio,
        enabled = state.sourceUris.isNotEmpty(),
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ZideeMinTouchTarget),
        shape = shapes.control,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        border = BorderStroke(1.dp, colors.borderSubtle),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.bgCanvas,
                contentColor = colors.textSecondary,
            ),
    ) {
        Text(
            text = "Buka Studio Editor (Crop & Split-Slider) →",
            style = typography.controlLabelSmall,
            fontWeight = FontWeight.Medium,
        )
    }

    StudioDualActionRow(
        canExport = state.canSubmit,
        targetShort = state.selectedPlatform.toActionTargetShort(),
        onSaveOnly = { onEvent(QuickShareUiEvent.ClickSaveOnly) },
        onSaveAndShare = { onEvent(QuickShareUiEvent.ClickSaveAndShare()) },
    )

    Text(
        text = state.selectedPlatform.toDirectPackageHint(),
        style = typography.metricMono,
        color = colors.textMuted,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    )
}

@Composable
private fun QuickShareMediaPreviewCard(state: QuickShareUiState) {
    val colors = ZideeTheme.colors
    val context = LocalContext.current
    val primaryUri = state.sourceUris.firstOrNull()
    val primaryMeta = state.inspectedSources.firstOrNull()
    val bitmap =
        rememberMediaThumbnail(
            uri = primaryUri?.value,
            mediaType = primaryMeta?.mediaType,
            maxDimensionPx = 220,
        )
    val baseName = primaryUri?.let { resolveUriDisplayName(context, it) } ?: "Media Pilihan"
    val title =
        if (state.sourceUris.size > 1) {
            "$baseName (+${state.sourceUris.size - 1} lainnya)"
        } else {
            baseName
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.bgCanvas,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickShareThumbnailFrame(
                bitmap = bitmap,
                title = title,
                isInspecting = state.isInspecting,
            )
            QuickShareMetadataSummary(
                title = title,
                primaryMeta = primaryMeta,
                activeSpec = state.activeSpec,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickShareThumbnailFrame(
    bitmap: ImageBitmap?,
    title: String,
    isInspecting: Boolean,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = Modifier.size(width = 56.dp, height = 72.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.bgElevated,
        border = BorderStroke(1.dp, colors.accentAmber.copy(alpha = 0.50f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isInspecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.accentAmber,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "9:16",
                    style = typography.metricMono,
                    color = colors.accentAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun QuickShareMetadataSummary(
    title: String,
    primaryMeta: MediaMetadata?,
    activeSpec: OptimalMediaSpec?,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            style = typography.controlLabelSmall,
            color = colors.contrastWhite,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = primaryMeta.toAsliSummaryLine(),
            style = typography.metricMono,
            color = colors.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = activeSpec.toTargetSummaryLine(),
            style = typography.metricMono,
            color = colors.accentAmberBright,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = (primaryMeta?.mediaType ?: MediaType.PHOTO).toDestinationAlbumHint(),
            style = typography.metricMono,
            color = colors.statusSuccess,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickShareProcessingBody(
    state: QuickShareUiState,
    onEvent: (QuickShareUiEvent) -> Unit,
    onShareCompletedItems: (List<OptimizedMediaItem>, TargetPlatform) -> Unit,
    onDismissSheet: () -> Unit,
) {
    val activeType = state.inspectedSources.firstOrNull()?.mediaType
    val engineLabel =
        when {
            activeType == MediaType.PHOTO -> "MEMPROSES FOTO OFFLINE (LANCZOS-3)"
            state.videoConfig.encoder == VideoEncoder.CPU_LIBX264 -> "MERENDER OFFLINE (CPU libx264)"
            else -> "MERENDER OFFLINE (GPU MediaCodec)"
        }
    val resText =
        state.activeSpec?.let {
            MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height)
        } ?: "1080 × 1920"
    val stageLabel =
        if (state.savingPart != null && state.totalParts != null) {
            "Menyimpan ke Galeri (Part ${state.savingPart}/${state.totalParts})..."
        } else {
            "Tahap: Filtergraph Lanczos-3 + Unsharp + ${state.selectedAspectMode.name}"
        }
    val folderPath = ZideeAlbum.relativePathFor(activeType ?: MediaType.VIDEO)

    RenderProgressAndResultView(
        isRunning = state.isProcessing,
        progressFraction = state.progressFraction,
        engineLabel = engineLabel,
        specDetailLabel = "Item ${state.currentItemIndex.coerceAtLeast(1)}/${state.totalItems.coerceAtLeast(1)} · $resText",
        stageLabel = stageLabel,
        targetPathLabel = "Target: $folderPath/",
        completedItems = state.completedItems,
        platform = state.selectedPlatform,
        doneButtonLabel = "Tutup & Kembali ke Galeri",
        onCancel = { onEvent(QuickShareUiEvent.ClickCancel) },
        onShareItem = { item -> onShareCompletedItems(listOf(item), state.selectedPlatform) },
        onShareAll = { items -> onShareCompletedItems(items, state.selectedPlatform) },
        onDoneNavigate = onDismissSheet,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 360, heightDp = 800)
@Composable
private fun QuickShareSheetPreview() {
    val uri = MediaUri("content://media/external/video/media/1420")
    val meta =
        MediaMetadata(
            uri = uri,
            mediaType = MediaType.VIDEO,
            width = 3840,
            height = 2160,
            sizeBytes = 58_400_000L,
            durationMs = 45_000L,
            frameRate = 60.0,
            isHdr = true,
        )
    val spec =
        OptimalMediaSpec(
            platform = TargetPlatform.WA_STORY,
            aspectMode = CanvasAspectMode.BLUR_BG_9_16,
            mediaType = MediaType.VIDEO,
            sourceSize = PixelSize(3840, 2160),
            outputSize = PixelSize(1080, 1920),
            geometry = CanvasGeometry.Scale,
            photo = PhotoEncodingSpec(ScalingAlgorithm.LANCZOS3, 28, 0.42, 94, true),
            video =
                VideoEncodingSpec(
                    frameRate = 30.0,
                    encoder = VideoEncoder.CPU_LIBX264,
                    maxBitrateKbps = 2_400,
                    bufferSizeKbps = 4_800,
                    audioBitrateKbps = 128,
                    audioSampleRateHz = 44_100,
                    sharpen = true,
                    applyHdrToSdr = true,
                    segmentSeconds = 30,
                    segmentDurationsMs = listOf(30_000L, 15_000L),
                ),
            estimatedSizeBytes = 6_900_000L,
        )
    ZideeTheme {
        QuickShareSheet(
            state =
                QuickShareUiState(
                    sourceUris = listOf(uri),
                    inspectedSources = listOf(meta),
                    activeSpec = spec,
                ),
            onEvent = {},
            onOpenFullStudio = {},
            onShareCompletedItems = { _, _ -> },
            onDismissSheet = {},
        )
    }
}
