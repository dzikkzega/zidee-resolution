package com.zidee.ress.presentation.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.zidee.ress.designsystem.component.AdvancedTuningEvent
import com.zidee.ress.designsystem.component.CanvasModeSegmentedControl
import com.zidee.ress.designsystem.component.PlatformPresetSelector
import com.zidee.ress.designsystem.component.zideeTactileScale
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.model.ZideeAlbum
import com.zidee.ress.presentation.common.ErrorAlertBanner
import com.zidee.ress.presentation.common.RenderProgressAndResultView
import com.zidee.ress.presentation.common.ZideePresentationDefaults
import com.zidee.ress.presentation.common.buildAdvancedTuningUiState
import com.zidee.ress.presentation.common.dispatchAdvancedTuningEvent
import com.zidee.ress.presentation.common.formatAdvancedSummaryBadge
import com.zidee.ress.presentation.common.resolveUriDisplayName
import com.zidee.ress.presentation.common.toActionTargetShort
import com.zidee.ress.presentation.common.toSourceSpecComparisonLine

@Composable
internal fun StudioPhoneLayout(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    onBack: () -> Unit,
    onAddMoreMedia: () -> Unit,
    onShareCompletedItems: (List<OptimizedMediaItem>, TargetPlatform) -> Unit,
    onDoneToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors

    when (val processing = state.processingState) {
        is StudioProcessingState.Running,
        is StudioProcessingState.Completed,
        is StudioProcessingState.PartialSuccess,
        -> {
            StudioPhoneProcessingScreen(
                state = state,
                processing = processing,
                onEvent = onEvent,
                onShareCompletedItems = onShareCompletedItems,
                onDoneToDashboard = onDoneToDashboard,
                modifier = modifier,
            )
            return
        }
        StudioProcessingState.Idle -> Unit
    }

    if (state.isAdvancedExpanded) {
        StudioPhoneAdvancedSheet(
            state = state,
            onEvent = onEvent,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bgCanvas),
    ) {
        StudioPhoneHeader(
            state = state,
            onBack = onBack,
            onEvent = onEvent,
            onAddMoreMedia = onAddMoreMedia,
        )

        if (!state.errorMessage.isNullOrBlank()) {
            ErrorAlertBanner(
                message = state.errorMessage,
                onDismiss = { onEvent(StudioEditorUiEvent.DismissError) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        StudioSplitPreviewCanvas(
            state = state,
            onEvent = onEvent,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        StudioPhoneBottomDeck(state = state, onEvent = onEvent)
    }
}

@Composable
private fun StudioPhoneHeader(
    state: StudioEditorUiState,
    onBack: () -> Unit,
    onEvent: (StudioEditorUiEvent) -> Unit,
    onAddMoreMedia: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val context = LocalContext.current
    val activeEntry = state.activeEntry
    val fileTitle = activeEntry?.let { resolveUriDisplayName(context, it.uri) } ?: "Studio Editor"
    val specLine = activeEntry?.metadata.toSourceSpecComparisonLine(state.activeSpec)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudioPhoneTitleBar(fileTitle = fileTitle, onBack = onBack)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.accentAmber.copy(alpha = 0.28f)),
        ) {
            Text(
                text = specLine,
                style = typography.metricMono,
                color = colors.accentAmberBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }

        if (state.queue.size > 1) {
            StudioPhoneQueueStrip(
                queueSize = state.queue.size,
                activeIndex = state.activeIndex,
                onSelectIndex = { onEvent(StudioEditorUiEvent.SelectQueueIndex(it)) },
                onAddMoreMedia = onAddMoreMedia,
            )
        }
    }
}

@Composable
private fun StudioPhoneTitleBar(
    fileTitle: String,
    onBack: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(ZideeMinTouchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Kembali ke Dashboard",
                    tint = colors.contrastWhite,
                )
            }
            Text(
                text = fileTitle,
                style = typography.sectionHeader,
                color = colors.contrastWhite,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "STUDIO EDITOR · HD+",
            style = typography.metricMono,
            color = colors.accentAmber,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StudioPhoneQueueStrip(
    queueSize: Int,
    activeIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onAddMoreMedia: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(queueSize) { index ->
            val selected = index == activeIndex
            Box(
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = ZideeMinTouchTarget)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectIndex(index) },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) colors.accentAmberSoft else colors.bgSurface,
                    border = BorderStroke(1.dp, if (selected) colors.accentAmber else colors.borderSubtle),
                ) {
                    Text(
                        text = "Media ${index + 1}",
                        style = typography.metricMono,
                        color = if (selected) colors.accentAmberBright else colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = ZideeMinTouchTarget)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAddMoreMedia),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.bgElevated,
                border = BorderStroke(1.dp, colors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Tambah Media",
                        tint = colors.contrastWhite,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "Batch",
                        style = typography.metricMono,
                        color = colors.contrastWhite,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioPhoneBottomDeck(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val activeType = state.activeEntry?.metadata?.mediaType
    val summaryBadge = formatAdvancedSummaryBadge(activeType, state.photoConfig, state.videoConfig)
    val tuningUiState =
        buildAdvancedTuningUiState(
            mediaType = activeType,
            platform = state.selectedPlatform,
            photoConfig = state.photoConfig,
            videoConfig = state.videoConfig,
            activeSpec = state.activeSpec,
        )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StudioPhoneDeckSelectors(state = state, onEvent = onEvent)
            AdvancedParameterPanel(
                expanded = false,
                onToggleExpand = { onEvent(StudioEditorUiEvent.ToggleAdvancedPanel) },
                summaryBadge = summaryBadge,
                state = tuningUiState,
                onEvent = {},
            )
            Text(
                text = "✓ Otomatis disimpan permanen ke Galeri/ZideeResolution",
                style = typography.metricMono,
                color = colors.statusSuccess,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            StudioDualActionRow(
                canExport = state.canExport,
                targetShort = state.selectedPlatform.toActionTargetShort(),
                onSaveOnly = { onEvent(StudioEditorUiEvent.SaveToGallery) },
                onSaveAndShare = { onEvent(StudioEditorUiEvent.SaveAndShare()) },
            )
        }
    }
}

@Composable
private fun StudioPhoneDeckSelectors(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val targetRes =
        state.activeSpec?.let {
            MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height)
        } ?: "1080 × 1920"

    Text(
        text = "1. TARGET PLATFORM OPTIMASI",
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
                onEvent(StudioEditorUiEvent.SelectPlatform(it))
            }
        },
    )
    Text(
        text = "2. MODE KANVAS & RASIO ($targetRes)",
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
                onEvent(StudioEditorUiEvent.SelectAspectMode(it))
            }
        },
    )
}

@Composable
internal fun StudioDualActionRow(
    canExport: Boolean,
    targetShort: String,
    onSaveOnly: () -> Unit,
    onSaveAndShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onSaveOnly,
            enabled = canExport,
            modifier =
                Modifier
                    .weight(0.40f)
                    .height(50.dp),
            shape = shapes.control,
            contentPadding = PaddingValues(horizontal = 10.dp),
            border = BorderStroke(1.dp, colors.borderSubtle),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.bgElevated,
                    contentColor = colors.contrastWhite,
                ),
        ) {
            Text(
                text = "Simpan Saja",
                style = typography.controlLabelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onSaveAndShare,
            enabled = canExport,
            interactionSource = interactionSource,
            modifier =
                Modifier
                    .weight(0.60f)
                    .height(50.dp)
                    .zideeTactileScale(interactionSource = interactionSource, enabled = canExport),
            shape = shapes.control,
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colors.accentAmber,
                    contentColor = colors.onAccent,
                ),
        ) {
            Text(
                text = "Simpan & Kirim ke $targetShort",
                style = typography.controlLabelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StudioPhoneAdvancedSheet(
    state: StudioEditorUiState,
    onEvent: (StudioEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
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
    val dispatchTuning: (AdvancedTuningEvent) -> Unit = { tuningEvent ->
        dispatchAdvancedTuningEvent(
            event = tuningEvent,
            platform = state.selectedPlatform,
            onUpdatePhoto = { transform -> onEvent(StudioEditorUiEvent.UpdatePhotoConfig(transform)) },
            onUpdateVideo = { transform -> onEvent(StudioEditorUiEvent.UpdateVideoConfig(transform)) },
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bgCanvas),
    ) {
        AdvancedSheetTopBar(onResetAuto = { dispatchTuning(AdvancedTuningEvent.ResetAllToOptimal) })
        HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            AdvancedParameterPanel(
                expanded = true,
                onToggleExpand = { onEvent(StudioEditorUiEvent.ToggleAdvancedPanel) },
                summaryBadge = summaryBadge,
                state = tuningState,
                onEvent = dispatchTuning,
                showHeader = false,
                showResetAllButton = false,
            )
        }
        Surface(modifier = Modifier.fillMaxWidth(), color = colors.bgSurface) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { onEvent(StudioEditorUiEvent.ToggleAdvancedPanel) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    shape = shapes.control,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.accentAmber,
                            contentColor = colors.onAccent,
                        ),
                ) {
                    Text(
                        text = "Terapkan Pengaturan & Kembali ke Kanvas",
                        style = typography.controlLabel,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSheetTopBar(onResetAuto: () -> Unit) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "PRO OPTICAL ENGINE · v1.0",
            style = typography.metricMono,
            color = colors.accentAmber,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = colors.accentAmber,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Pengaturan Advanced Pro",
                    style = typography.sectionHeader,
                    color = colors.contrastWhite,
                    fontSize = 17.sp,
                )
            }
            OutlinedButton(
                onClick = onResetAuto,
                modifier = Modifier.defaultMinSize(minHeight = ZideeMinTouchTarget),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, colors.borderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentAmberBright),
            ) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = "Reset Auto", style = typography.controlLabelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StudioPhoneProcessingScreen(
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
    val resText =
        state.activeSpec?.let {
            MediaFormatters.formatResolution(it.outputSize.width, it.outputSize.height)
        } ?: "1080 × 1920"
    val fpsText = state.activeSpec?.video?.frameRate?.let { " ${it.toInt()}fps" }.orEmpty()
    val specDetail =
        if (running != null) {
            "Item ${running.currentItemIndex}/${running.totalItems} · $resText$fpsText"
        } else {
            "$resText$fpsText"
        }
    val stageLabel =
        if (running?.savingPart != null && running.totalParts != null) {
            "Menyimpan ke Galeri (Part ${running.savingPart}/${running.totalParts})..."
        } else {
            "Tahap: Filtergraph Lanczos-3 + Unsharp + ${state.selectedAspectMode.name}"
        }
    val folderPath = ZideeAlbum.relativePathFor(activeType ?: MediaType.VIDEO)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bgCanvas)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
    ) {
        RenderProgressAndResultView(
            isRunning = running != null,
            progressFraction = running?.progressFraction ?: 1f,
            engineLabel = engineLabel,
            specDetailLabel = specDetail,
            stageLabel = stageLabel,
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
        )
    }
}
