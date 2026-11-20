package com.zidee.ress.presentation.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zidee.ress.core.format.MediaFormatters
import com.zidee.ress.designsystem.component.AdvancedParameterPanel
import com.zidee.ress.designsystem.component.CanvasModeSegmentedControl
import com.zidee.ress.designsystem.component.PlatformPresetSelector
import com.zidee.ress.designsystem.component.zideeTactileScale
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.model.ZideeAlbum
import com.zidee.ress.presentation.common.ErrorAlertBanner
import com.zidee.ress.presentation.common.MiniLogoBadge
import com.zidee.ress.presentation.common.OfflineEnginePill
import com.zidee.ress.presentation.common.RenderProgressAndResultView
import com.zidee.ress.presentation.common.ZideePresentationDefaults
import com.zidee.ress.presentation.common.buildAdvancedTuningUiState
import com.zidee.ress.presentation.common.dispatchAdvancedTuningEvent
import com.zidee.ress.presentation.common.formatAdvancedSummaryBadge
import com.zidee.ress.presentation.common.resolveUriDisplayName
import com.zidee.ress.presentation.common.toActionTargetShort
import com.zidee.ress.presentation.common.toFullTitle
import com.zidee.ress.presentation.common.toShortLabel
import com.zidee.ress.presentation.common.toSourceSpecComparisonLine
import kotlin.math.roundToInt

@Composable
internal fun StudioTabletPortraitLayout(
    state: StudioEditorUiState,
    historyItems: List<OptimizedMediaItem>,
    onEvent: (StudioEditorUiEvent) -> Unit,
    onBack: () -> Unit,
    onAddMoreMedia: () -> Unit,
    onReShareHistoryItem: (OptimizedMediaItem) -> Unit,
    onShareCompletedItems: (List<OptimizedMediaItem>, TargetPlatform) -> Unit,
    onDoneToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val processing = state.processingState
    if (processing is StudioProcessingState.Running ||
        processing is StudioProcessingState.Completed ||
        processing is StudioProcessingState.PartialSuccess
    ) {
        TabletProcessingOverlay(
            state = state,
            processing = processing,
            onEvent = onEvent,
            onShareCompletedItems = onShareCompletedItems,
            onDoneToDashboard = onDoneToDashboard,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().background(colors.bgCanvas)) {
        TabletTopHeaderBar(
            title = "Zidee Ress Studio",
            subtitle = "TABLET PORTRAIT · 100% OFFLINE ENGINE",
            specLine = state.activeEntry?.metadata.toSourceSpecComparisonLine(state.activeSpec),
            rightPillText = "● ZideeResolution",
            historyCount = null,
            onBack = onBack,
        )
        if (!state.errorMessage.isNullOrBlank()) {
            ErrorAlertBanner(
                message = state.errorMessage,
                onDismiss = { onEvent(StudioEditorUiEvent.DismissError) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        TabletPortraitUpperRow(
            state = state,
            historyItems = historyItems,
            onEvent = onEvent,
            onAddMoreMedia = onAddMoreMedia,
            onReShareHistoryItem = onReShareHistoryItem,
            modifier = Modifier.weight(0.54f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        TabletPortraitLowerDeck(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.weight(0.46f).fillMaxWidth().padding(horizontal = 16.dp),
        )
        TabletPortraitBottomBar(state = state, onEvent = onEvent)
    }
}

@Composable
internal fun StudioTabletLandscapeLayout(
    state: StudioEditorUiState,
    historyItems: List<OptimizedMediaItem>,
    onEvent: (StudioEditorUiEvent) -> Unit,
    onBack: () -> Unit,
    onAddMoreMedia: () -> Unit,
    onReShareHistoryItem: (OptimizedMediaItem) -> Unit,
    onShareCompletedItems: (List<OptimizedMediaItem>, TargetPlatform) -> Unit,
    onDoneToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val processing = state.processingState
    if (processing is StudioProcessingState.Running ||
        processing is StudioProcessingState.Completed ||
        processing is StudioProcessingState.PartialSuccess
    ) {
        TabletProcessingOverlay(
            state = state,
            processing = processing,
            onEvent = onEvent,
            onShareCompletedItems = onShareCompletedItems,
            onDoneToDashboard = onDoneToDashboard,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().background(colors.bgCanvas).navigationBarsPadding()) {
        TabletTopHeaderBar(
            title = "Zidee Ress Workstation",
            subtitle = "TABLET LANDSCAPE · LANCZOS-3 + FFMPEG OFFLINE",
            specLine = state.activeEntry?.metadata.toSourceSpecComparisonLine(state.activeSpec),
            rightPillText = "● ON-DEVICE",
            historyCount = historyItems.size,
            onBack = onBack,
        )
        if (!state.errorMessage.isNullOrBlank()) {
            ErrorAlertBanner(
                message = state.errorMessage,
                onDismiss = { onEvent(StudioEditorUiEvent.DismissError) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TabletQueueAndAlbumPane(
                headerTitle = "ANTRIAN BATCH & ALBUM (${state.queue.size + historyItems.size})",
                buttonLabel = "+ Pilih Media (Batch)",
                state = state,
                historyItems = historyItems,
                onSelectQueueIndex = { onEvent(StudioEditorUiEvent.SelectQueueIndex(it)) },
                onAddMoreMedia = onAddMoreMedia,
                onReShareHistoryItem = onReShareHistoryItem,
                modifier = Modifier.width(248.dp).fillMaxHeight(),
            )
            TabletCenterCanvasPane(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            TabletRightInspectorPane(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.width(316.dp).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun TabletTopHeaderBar(
    title: String,
    subtitle: String,
    specLine: String,
    rightPillText: String,
    historyCount: Int?,
    onBack: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(modifier = Modifier.fillMaxWidth(), color = colors.bgSurface) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(ZideeMinTouchTarget)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", tint = colors.contrastWhite)
                }
                MiniLogoBadge(versionLabel = "v1.0")
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = typography.sectionHeader,
                        color = colors.contrastWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(subtitle, style = typography.metricMono, color = colors.textSecondary, fontSize = 10.sp, maxLines = 1)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.bgCanvas,
                    border = BorderStroke(1.dp, colors.accentAmber.copy(alpha = 0.30f)),
                ) {
                    Text(
                        text = specLine,
                        style = typography.metricMono,
                        color = colors.accentAmberBright,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                if (historyCount != null) {
                    OfflineEnginePill(text = "ZideeResolution ($historyCount)")
                }
                OfflineEnginePill(text = rightPillText)
            }
            HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
        }
    }
}

@Composable
private fun TabletPortraitUpperRow(
    state: StudioEditorUiState,
    historyItems: List<OptimizedMediaItem>,
    onEvent: (StudioEditorUiEvent) -> Unit,
    onAddMoreMedia: () -> Unit,
    onReShareHistoryItem: (OptimizedMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TabletQueueAndAlbumPane(
            headerTitle = "ANTRIAN & RIWAYAT (${state.queue.size + historyItems.size})",
            buttonLabel = "+ Tambah Media (Batch)",
            state = state,
            historyItems = historyItems,
            onSelectQueueIndex = { onEvent(StudioEditorUiEvent.SelectQueueIndex(it)) },
            onAddMoreMedia = onAddMoreMedia,
            onReShareHistoryItem = onReShareHistoryItem,
            modifier = Modifier.width(216.dp).fillMaxHeight(),
        )
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.borderSubtle),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TabletPortraitCanvasHeader(state = state)
                StudioSplitPreviewCanvas(
                    state = state,
                    onEvent = onEvent,
                    showBottomPill = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TabletPortraitCanvasHeader(state: StudioEditorUiState) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val resText = state.activeSpec?.let { MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height) } ?: "1080 × 1920"
    val ratioTag = if (state.selectedAspectMode == CanvasAspectMode.ORIGINAL) "KANVAS ASLI" else "KANVAS 9:16"
    val blurSuffix =
        if (state.selectedAspectMode == CanvasAspectMode.BLUR_BG_9_16) {
            " · Blur BG: ${state.blurRadiusPx}px"
        } else {
            ""
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$ratioTag · $resText px$blurSuffix",
            style = typography.metricMono,
            color = colors.accentAmberBright,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "GESER GARIS ◄ ► · Simulasi Kanvas",
            style = typography.metricMono,
            color = colors.textSecondary,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TabletPortraitLowerDeck(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabletPortraitControlCard(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        TabletPortraitAdvancedCard(
            state = state,
            onEvent = onEvent,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun TabletPortraitControlCard(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val albumPath = ZideeAlbum.relativePathFor(state.activeEntry?.metadata?.mediaType ?: MediaType.VIDEO)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlatformAndCanvasSelectors(
                selectedPlatform = state.selectedPlatform,
                selectedAspectMode = state.selectedAspectMode,
                platformSectionLabel = "1. TARGET PLATFORM OPTIMASI",
                canvasSectionLabel = "2. MODE KANVAS & RASIO (3 MODE)",
                onSelectPlatform = { onEvent(StudioEditorUiEvent.SelectPlatform(it)) },
                onSelectAspectMode = { onEvent(StudioEditorUiEvent.SelectAspectMode(it)) },
            )
            if (state.selectedAspectMode == CanvasAspectMode.BLUR_BG_9_16) {
                BlurIntensitySliderCard(
                    blurRadiusPx = state.blurRadiusPx,
                    onChange = { onEvent(StudioEditorUiEvent.UpdateBlurRadius(it)) },
                )
            }
            Text(
                text = "✓ Simpan permanen: $albumPath",
                style = typography.metricMono,
                color = colors.statusSuccess,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun BlurIntensitySliderCard(
    blurRadiusPx: Int,
    onChange: (Int) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.bgCanvas,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Intensitas Blur Background 9:16",
                    style = typography.controlLabelSmall,
                    color = colors.contrastWhite,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$blurRadiusPx px",
                    style = typography.metricBadge,
                    color = colors.accentAmber,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = blurRadiusPx.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = CanvasGeometry.MIN_BLUR_RADIUS_PX.toFloat()..CanvasGeometry.MAX_BLUR_RADIUS_PX.toFloat(),
                colors =
                    SliderDefaults.colors(
                        thumbColor = colors.accentAmber,
                        activeTrackColor = colors.accentAmber,
                        inactiveTrackColor = colors.bgElevated,
                    ),
            )
        }
    }
}

@Composable
private fun TabletPortraitAdvancedCard(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.accentAmber.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "3. ⚙ PENGATURAN ADVANCED PRO (SELALU AKTIF)",
                style = typography.metricMono,
                color = colors.accentAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            StudioInlineAdvancedPanel(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun PlatformAndCanvasSelectors(
    selectedPlatform: TargetPlatform,
    selectedAspectMode: CanvasAspectMode,
    platformSectionLabel: String,
    canvasSectionLabel: String,
    onSelectPlatform: (TargetPlatform) -> Unit,
    onSelectAspectMode: (CanvasAspectMode) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Text(
        text = platformSectionLabel,
        style = typography.metricMono,
        color = colors.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
    )
    PlatformPresetSelector(
        options = ZideePresentationDefaults.platformOptions,
        selectedId = selectedPlatform.name,
        wrapItems = true,
        onSelect = { rawId -> TargetPlatform.entries.firstOrNull { it.name == rawId }?.let(onSelectPlatform) },
    )
    Text(
        text = canvasSectionLabel,
        style = typography.metricMono,
        color = colors.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
    )
    CanvasModeSegmentedControl(
        options = ZideePresentationDefaults.canvasModeOptions,
        selectedId = selectedAspectMode.name,
        onSelect = { rawId -> CanvasAspectMode.entries.firstOrNull { it.name == rawId }?.let(onSelectAspectMode) },
    )
}

@Composable
private fun StudioInlineAdvancedPanel(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
) {
    val activeType = state.activeEntry?.metadata?.mediaType
    val summaryBadge = formatAdvancedSummaryBadge(activeType, state.photoConfig, state.videoConfig)
    val tuningState =
        buildAdvancedTuningUiState(
            mediaType = activeType,
            platform = state.selectedPlatform,
            photoConfig = state.photoConfig,
            videoConfig = state.videoConfig,
            activeSpec = state.activeSpec,
        )
    AdvancedParameterPanel(
        expanded = true,
        onToggleExpand = {},
        summaryBadge = summaryBadge,
        state = tuningState,
        onEvent = { tuningEvent ->
            dispatchAdvancedTuningEvent(
                event = tuningEvent,
                platform = state.selectedPlatform,
                onUpdatePhoto = { t -> onEvent(StudioEditorUiEvent.UpdatePhotoConfig(t)) },
                onUpdateVideo = { t -> onEvent(StudioEditorUiEvent.UpdateVideoConfig(t)) },
            )
        },
        showHeader = false,
    )
}

@Composable
private fun TabletPortraitBottomBar(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val albumPath = ZideeAlbum.relativePathFor(state.activeEntry?.metadata?.mediaType ?: MediaType.VIDEO)

    Surface(modifier = Modifier.fillMaxWidth(), color = colors.bgSurface) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "✓ Folder Output: Galeri/$albumPath",
                    style = typography.metricMono,
                    color = colors.statusSuccess,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StudioDualActionRow(
                    canExport = state.canExport,
                    targetShort = state.selectedPlatform.toActionTargetShort(),
                    onSaveOnly = { onEvent(StudioEditorUiEvent.SaveToGallery) },
                    onSaveAndShare = { onEvent(StudioEditorUiEvent.SaveAndShare()) },
                    modifier = Modifier.width(360.dp),
                )
            }
        }
    }
}

@Composable
private fun TabletQueueAndAlbumPane(
    headerTitle: String,
    buttonLabel: String,
    state: StudioEditorUiState,
    historyItems: List<OptimizedMediaItem>,
    onSelectQueueIndex: (Int) -> Unit,
    onAddMoreMedia: () -> Unit,
    onReShareHistoryItem: (OptimizedMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val context = LocalContext.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = headerTitle,
                style = typography.metricMono,
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onAddMoreMedia,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = ZideeMinTouchTarget),
                shape = shapes.controlInner,
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentAmber, contentColor = colors.onAccent),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(buttonLabel, style = typography.controlLabelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.queue) { index, entry ->
                    ActiveQueueItemCard(
                        title = resolveUriDisplayName(context, entry.uri),
                        isSelected = index == state.activeIndex,
                        platformLabel = state.selectedPlatform.toShortLabel(),
                        aspectLabel = state.selectedAspectMode.toShortLabel(),
                        onClick = { onSelectQueueIndex(index) },
                    )
                }
                itemsIndexed(items = historyItems.take(8), key = { _, item -> "hist_${item.id}" }) { _, item ->
                    SavedHistorySidebarCard(item = item, onReShare = { onReShareHistoryItem(item) })
                }
            }
        }
    }
}

@Composable
private fun ActiveQueueItemCard(
    title: String,
    isSelected: Boolean,
    platformLabel: String,
    aspectLabel: String,
    onClick: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) colors.bgElevated else colors.bgCanvas,
        border = BorderStroke(1.dp, if (isSelected) colors.accentAmber else colors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isSelected) "● SEDANG DIEDIT · $platformLabel" else "ANTRIAN · $platformLabel",
                style = typography.metricMono,
                color = if (isSelected) colors.accentAmber else colors.textSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = typography.controlLabelSmall,
                color = colors.contrastWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = aspectLabel, style = typography.metricMono, color = colors.statusSuccess, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SavedHistorySidebarCard(
    item: OptimizedMediaItem,
    onReShare: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val res = MediaFormatters.formatResolution(item.outputSize.width, item.outputSize.height)
    val bytes = MediaFormatters.formatBytes(item.outputSizeBytes)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.bgCanvas,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "✓ TERSIMPAN · ${item.platform.toShortLabel().uppercase()}",
                style = typography.metricMono,
                color = colors.statusSuccess,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.displayName,
                style = typography.controlLabelSmall,
                color = colors.contrastWhite,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "$res · $bytes", style = typography.metricMono, color = colors.textSecondary, fontSize = 9.sp)
            OutlinedButton(
                onClick = onReShare,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = ZideeMinTouchTarget),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, colors.borderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.bgElevated, contentColor = colors.contrastWhite),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Kirim Ulang ke ${item.platform.toActionTargetShort()} →",
                    style = typography.metricMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TabletCenterCanvasPane(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val resText = state.activeSpec?.let { MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height) } ?: "1080×1920"
    val footerModeText =
        if (state.selectedAspectMode == CanvasAspectMode.BLUR_BG_9_16) {
            "Mode Blur BG 9:16 (Radius ${state.blurRadiusPx}px) · Simulasi pratinjau kanvas (hasil nyata setelah ekspor)"
        } else {
            "Mode ${state.selectedAspectMode.toShortLabel()} · Simulasi pratinjau kanvas (hasil nyata setelah ekspor)"
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "KANVAS PRATINJAU · $resText",
                    style = typography.metricMono,
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "SPLIT-SLIDER 120Hz · SIMULASI",
                    style = typography.metricMono,
                    color = colors.accentAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            StudioSplitPreviewCanvas(
                state = state,
                onEvent = onEvent,
                showBottomPill = false,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Text(
                text = footerModeText,
                style = typography.metricMono,
                color = colors.textSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun TabletRightInspectorPane(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val albumPath = ZideeAlbum.relativePathFor(state.activeEntry?.metadata?.mediaType ?: MediaType.VIDEO)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlatformAndCanvasSelectors(
                selectedPlatform = state.selectedPlatform,
                selectedAspectMode = state.selectedAspectMode,
                platformSectionLabel = "1. TARGET PLATFORM",
                canvasSectionLabel = "2. MODE KANVAS & RASIO",
                onSelectPlatform = { onEvent(StudioEditorUiEvent.SelectPlatform(it)) },
                onSelectAspectMode = { onEvent(StudioEditorUiEvent.SelectAspectMode(it)) },
            )
            if (state.selectedAspectMode == CanvasAspectMode.BLUR_BG_9_16) {
                BlurIntensitySliderCard(
                    blurRadiusPx = state.blurRadiusPx,
                    onChange = { onEvent(StudioEditorUiEvent.UpdateBlurRadius(it)) },
                )
            }
            Text(
                text = "3. ⚙ ADVANCED PRO INSPECTOR",
                style = typography.metricMono,
                color = colors.accentAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            StudioInlineAdvancedPanel(state = state, onEvent = onEvent)
            TabletRightActionFooter(albumPath = albumPath, state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun TabletRightActionFooter(
    albumPath: String,
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.statusSuccessSoft,
        border = BorderStroke(1.dp, colors.statusSuccess.copy(alpha = 0.40f)),
    ) {
        Text(
            text = "✓ Simpan Permanen ke Galeri:\n$albumPath",
            style = typography.metricMono,
            color = colors.statusSuccess,
            fontSize = 10.sp,
            modifier = Modifier.padding(10.dp),
        )
    }
    OutlinedButton(
        onClick = { onEvent(StudioEditorUiEvent.SaveToGallery) },
        enabled = state.canExport,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = shapes.control,
        border = BorderStroke(1.dp, colors.borderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.bgElevated, contentColor = colors.contrastWhite),
    ) {
        Text(text = "Simpan Saja ke ZideeResolution", style = typography.controlLabelSmall, fontWeight = FontWeight.Bold)
    }
    Button(
        onClick = { onEvent(StudioEditorUiEvent.SaveAndShare()) },
        enabled = state.canExport,
        interactionSource = interactionSource,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .zideeTactileScale(interactionSource = interactionSource, enabled = state.canExport),
        shape = shapes.control,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accentAmber, contentColor = colors.onAccent),
    ) {
        Text(
            text = "Simpan & Kirim ke ${state.selectedPlatform.toFullTitle()}",
            style = typography.controlLabelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TabletProcessingOverlay(
    state: StudioEditorUiState,
    processing: StudioProcessingState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    onShareCompletedItems: (List<OptimizedMediaItem>, TargetPlatform) -> Unit,
    onDoneToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val running = processing as? StudioProcessingState.Running
    val completed =
        when (processing) {
            is StudioProcessingState.Completed -> processing.items
            is StudioProcessingState.PartialSuccess -> processing.savedItems
            else -> emptyList()
        }
    val partialFailure = (processing as? StudioProcessingState.PartialSuccess)?.failureMessage
    val activeType = state.activeEntry?.metadata?.mediaType
    val engineLabel =
        when {
            activeType == MediaType.PHOTO -> "MEMPROSES FOTO OFFLINE (LANCZOS-3)"
            state.videoConfig.encoder == VideoEncoder.CPU_LIBX264 -> "MERENDER OFFLINE (CPU libx264)"
            else -> "MERENDER OFFLINE (GPU MediaCodec)"
        }
    val resText = state.activeSpec?.let { MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height) } ?: "1080 × 1920"
    val folderPath = ZideeAlbum.relativePathFor(activeType ?: MediaType.VIDEO)

    Box(
        modifier = modifier.fillMaxSize().background(colors.bgCanvas).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        RenderProgressAndResultView(
            isRunning = running != null,
            progressFraction = running?.progressFraction ?: 1f,
            engineLabel = engineLabel,
            specDetailLabel = resText,
            stageLabel = "Tahap: Filtergraph Lanczos-3 + Unsharp + ${state.selectedAspectMode.toShortLabel()}",
            targetPathLabel = "Target: $folderPath/",
            completedItems = completed,
            platform = state.selectedPlatform,
            partialFailureMessage = partialFailure,
            onDismissPartialError = { onEvent(StudioEditorUiEvent.DismissError) },
            onCancel = { onEvent(StudioEditorUiEvent.CancelOptimization) },
            onShareItem = { item -> onShareCompletedItems(listOf(item), state.selectedPlatform) },
            onShareAll = { items -> onShareCompletedItems(items, state.selectedPlatform) },
            onDoneNavigate = {
                onEvent(StudioEditorUiEvent.DismissCompleted)
                onDoneToDashboard()
            },
            modifier = Modifier.widthIn(max = 620.dp),
        )
    }
}
