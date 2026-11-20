package com.zidee.ress.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme
import kotlin.math.roundToInt

/**
 * Expandable `Advanced Pro` optical tuning panel matching FigCraft `HP 02` (collapsed pill bar),
 * `HP 03` (dedicated tuning screen with `showHeader = false`), and `TAB 01`/`TAB 02` inspectors.
 */
@Composable
fun AdvancedParameterPanel(
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    summaryBadge: String,
    state: AdvancedTuningUiState,
    onEvent: (AdvancedTuningEvent) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showResetAllButton: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showHeader) {
            AdvancedAccordionHeader(
                expanded = expanded,
                summaryBadge = summaryBadge,
                onToggleExpand = onToggleExpand,
            )
        }

        if (showHeader) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
                AdvancedExpandedBody(
                    state = state,
                    onEvent = onEvent,
                    showResetAllButton = showResetAllButton,
                )
            }
        } else if (expanded) {
            AdvancedExpandedBody(
                state = state,
                onEvent = onEvent,
                showResetAllButton = showResetAllButton,
            )
        }
    }
}

@Composable
private fun AdvancedAccordionHeader(
    expanded: Boolean,
    summaryBadge: String,
    onToggleExpand: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "AdvancedChevronRotation",
    )

    Surface(
        modifier =
            Modifier
                .testTag("advanced_panel_header")
                .fillMaxWidth()
                .defaultMinSize(minHeight = ZideeMinTouchTarget)
                .zideeTactileScale(interactionSource)
                .clip(shapes.control)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleExpand()
                    },
                ),
        shape = shapes.control,
        color = colors.bgElevated,
        border = BorderStroke(1.dp, if (expanded) colors.borderActive else colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = colors.accentAmber, modifier = Modifier.size(16.dp))
                Text(
                    text = "Advanced Pro",
                    style = typography.controlLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.contrastWhite,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                Text(
                    text = summaryBadge,
                    style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
                    color = colors.accentAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = if (expanded) "Tutup Advanced Pro" else "Buka Advanced Pro",
                    tint = colors.accentAmber,
                    modifier = Modifier.size(18.dp).rotate(chevronRotation),
                )
            }
        }
    }
}

@Composable
private fun AdvancedExpandedBody(
    state: AdvancedTuningUiState,
    onEvent: (AdvancedTuningEvent) -> Unit,
    showResetAllButton: Boolean,
) {
    val showPhotoControls = state.mediaMode != MediaTuningMode.VIDEO
    val showVideoControls = state.mediaMode != MediaTuningMode.PHOTO

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showResetAllButton) {
            ResetAllRow(onResetAll = { onEvent(AdvancedTuningEvent.ResetAllToOptimal) })
        }

        if (showPhotoControls) {
            PhotoTuningCard(state = state, onEvent = onEvent)
        }

        SharpeningCard(
            sharpnessPercent = state.sharpnessPercent,
            isModified = state.isSharpnessModified,
            onSharpnessChange = { onEvent(AdvancedTuningEvent.ChangeSharpness(it)) },
            onReset = { onEvent(AdvancedTuningEvent.ResetSharpness) },
        )

        if (showVideoControls) {
            VideoParamsCard(state = state, onEvent = onEvent)
            EncoderSelectionCard(
                selectedEncoder = state.encoder,
                onSelectEncoder = { onEvent(AdvancedTuningEvent.SelectEncoder(it)) },
            )
            ColorAndAutoSplitCard(
                hdrToSdr = state.hdrToSdr,
                autoSplit = state.autoSplit,
                onToggleHdrToSdr = { onEvent(AdvancedTuningEvent.ToggleHdrToSdr(it)) },
                onSelectAutoSplit = { onEvent(AdvancedTuningEvent.SelectAutoSplit(it)) },
            )
        }
    }
}

@Composable
private fun ResetAllRow(onResetAll: () -> Unit) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PRO OPTICAL ENGINE",
            style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
            color = colors.accentAmber,
        )
        Surface(
            modifier =
                Modifier
                    .testTag("advanced_reset_all")
                    .defaultMinSize(minWidth = ZideeMinTouchTarget, minHeight = ZideeMinTouchTarget)
                    .clip(shapes.badge)
                    .clickable(role = Role.Button, onClick = onResetAll),
            shape = shapes.badge,
            color = colors.bgElevated,
            border = BorderStroke(1.dp, colors.borderSubtle),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = colors.accentAmberBright,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Reset Auto",
                    style = typography.controlLabelSmall,
                    color = colors.accentAmberBright,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoTuningCard(
    state: AdvancedTuningUiState,
    onEvent: (AdvancedTuningEvent) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val jpegMin = AdvancedTuningUiState.MIN_JPEG_QUALITY.toFloat()
    val jpegMax = AdvancedTuningUiState.MAX_JPEG_QUALITY.toFloat()

    DarkroomParameterCard(title = "ALGORITMA RESAMPLING & RESOLUSI TARGET") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionChip(
                label = "Lanczos-3 (Tajam Maks)",
                selected = state.scalingAlgorithm == ResamplingOption.LANCZOS_3,
                onClick = { onEvent(AdvancedTuningEvent.SelectScaling(ResamplingOption.LANCZOS_3)) },
                modifier = Modifier.weight(1f),
            )
            OptionChip(
                label = "Bicubic Halus",
                selected = state.scalingAlgorithm == ResamplingOption.BICUBIC,
                onClick = { onEvent(AdvancedTuningEvent.SelectScaling(ResamplingOption.BICUBIC)) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AdvancedTuningUiState.PHOTO_TARGET_RESOLUTIONS.forEach { px ->
                val label = if (px == null) "Preset Otomatis" else "${px}px"
                OptionChip(
                    label = label,
                    selected = state.photoTargetResolutionPx == px,
                    onClick = { onEvent(AdvancedTuningEvent.SelectPhotoResolution(px)) },
                    fillWidth = false,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        SliderHeaderWithReset(
            label = "Kualitas JPEG",
            valueText = "${state.jpegQuality}%",
            showReset = state.isJpegQualityModified,
            resetContentDescription = "Reset kualitas JPEG ke optimal",
            onReset = { onEvent(AdvancedTuningEvent.ResetJpegQuality) },
        )

        Slider(
            value = state.jpegQuality.toFloat(),
            onValueChange = { value ->
                val clamped = AdvancedTuningUiState.clampJpegQuality(value.roundToInt())
                onEvent(AdvancedTuningEvent.ChangeJpegQuality(clamped))
            },
            valueRange = jpegMin..jpegMax,
            colors = zideeSliderColors(),
        )

        ToggleParameterRow(
            label = "Auto sRGB Fix (Display P3 → sRGB)",
            checked = state.autoSrgbFix,
            onCheckedChange = { onEvent(AdvancedTuningEvent.ToggleAutoSrgbFix(it)) },
        )

        Text(
            text = state.outputSummaryLine,
            style = typography.metricMono,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SharpeningCard(
    sharpnessPercent: Int,
    isModified: Boolean,
    onSharpnessChange: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val minSharp = AdvancedTuningUiState.MIN_SHARPNESS_PERCENT.toFloat()
    val maxSharp = AdvancedTuningUiState.MAX_SHARPNESS_PERCENT.toFloat()

    DarkroomParameterCard(title = null) {
        SliderHeaderWithReset(
            label = "Anti-Blur Sharpening (Unsharp Mask)",
            valueText = "$sharpnessPercent%",
            showReset = isModified,
            resetContentDescription = "Reset ketajaman ke preset optimal",
            onReset = onReset,
        )

        Slider(
            value = sharpnessPercent.toFloat(),
            onValueChange = { value ->
                onSharpnessChange(AdvancedTuningUiState.clampSharpness(value.roundToInt()))
            },
            valueRange = minSharp..maxSharp,
            colors = zideeSliderColors(),
        )

        Text(
            text = "Radius: 1.2px · Threshold: 4 (Bebas Noise)",
            style = typography.metricMono,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "✓ Kompensasi anti-lembek sebelum kompresi WA/IG",
            style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
            color = colors.statusSuccess,
        )
    }
}

@Composable
private fun VideoParamsCard(
    state: AdvancedTuningUiState,
    onEvent: (AdvancedTuningEvent) -> Unit,
) {
    DarkroomParameterCard(title = "PARAMETER VIDEO STORY & BITRATE CEILING") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionChip(
                label = "1080p Full HD",
                selected = state.videoResolution == VideoResolutionOption.FHD_1080P,
                onClick = { onEvent(AdvancedTuningEvent.SelectVideoResolution(VideoResolutionOption.FHD_1080P)) },
                modifier = Modifier.weight(1f),
            )
            OptionChip(
                label = "720p Ultra-Sharp WA",
                selected = state.videoResolution == VideoResolutionOption.HD_720P_ULTRA_SHARP,
                onClick = {
                    onEvent(AdvancedTuningEvent.SelectVideoResolution(VideoResolutionOption.HD_720P_ULTRA_SHARP))
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionChip(
                label = "Lock 30 FPS",
                selected = state.frameRateMode == FrameRateOption.LOCK_30,
                onClick = { onEvent(AdvancedTuningEvent.SelectFrameRate(FrameRateOption.LOCK_30)) },
                modifier = Modifier.weight(1f),
            )
            OptionChip(
                label = "60 FPS",
                selected = state.frameRateMode == FrameRateOption.FPS_60,
                onClick = { onEvent(AdvancedTuningEvent.SelectFrameRate(FrameRateOption.FPS_60)) },
                modifier = Modifier.weight(1f),
            )
            OptionChip(
                label = "Asli",
                selected = state.frameRateMode == FrameRateOption.ORIGINAL,
                onClick = { onEvent(AdvancedTuningEvent.SelectFrameRate(FrameRateOption.ORIGINAL)) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        VideoBitrateSection(state = state, onEvent = onEvent)
    }
}

@Composable
private fun VideoBitrateSection(
    state: AdvancedTuningUiState,
    onEvent: (AdvancedTuningEvent) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val minBitrate = AdvancedTuningUiState.MIN_BITRATE_KBPS.toFloat()
    val maxBitrate = AdvancedTuningUiState.MAX_BITRATE_KBPS.toFloat()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OptionChip(
            label = "Bitrate Auto",
            selected = !state.isManualBitrate,
            onClick = { onEvent(AdvancedTuningEvent.ToggleManualBitrate(false)) },
            modifier = Modifier.weight(1f),
        )
        OptionChip(
            label = "Manual Mbps",
            selected = state.isManualBitrate,
            onClick = { onEvent(AdvancedTuningEvent.ToggleManualBitrate(true)) },
            modifier = Modifier.weight(1f),
        )
    }

    if (state.isManualBitrate) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Target Bitrate Manual",
                style = typography.caption,
                color = colors.textSecondary,
            )
            Text(
                text = state.formatBitrateMbps(),
                style = typography.metricBadge,
                color = colors.accentAmber,
            )
        }
        Slider(
            value = state.manualBitrateKbps.toFloat(),
            onValueChange = { kbps ->
                val clamped = AdvancedTuningUiState.clampManualBitrateKbps(kbps.roundToInt())
                onEvent(AdvancedTuningEvent.ChangeManualBitrateKbps(clamped))
            },
            valueRange = minBitrate..maxBitrate,
            colors = zideeSliderColors(),
        )
    } else {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Bitrate: ${state.autoBitrateSummary}",
            style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
            color = colors.contrastWhite,
        )
    }
}

@Composable
private fun EncoderSelectionCard(
    selectedEncoder: EncoderOption,
    onSelectEncoder: (EncoderOption) -> Unit,
) {
    DarkroomParameterCard(title = "MESIN ENCODER OFFLINE (DI HP USER)") {
        EncoderOptionTile(
            title = "Kualitas Maksimal (CPU · libx264)",
            description = "Ketajaman terbaik di WA/IG Story. Proses lebih lama pada HP spek rendah.",
            selected = selectedEncoder == EncoderOption.CPU_LIBX264,
            onClick = { onSelectEncoder(EncoderOption.CPU_LIBX264) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        EncoderOptionTile(
            title = "Mode Cepat (Hardware GPU · MediaCodec)",
            description = "Render 3x–5x lebih cepat menggunakan chip hardware HP.",
            selected = selectedEncoder == EncoderOption.GPU_MEDIACODEC,
            onClick = { onSelectEncoder(EncoderOption.GPU_MEDIACODEC) },
        )
    }
}

@Composable
private fun EncoderOptionTile(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier =
            Modifier
                .testTag("advanced_encoder_tile_$title")
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .zideeTactileScale(interactionSource)
                .clip(shapes.control)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                )
                .semantics {
                    this.selected = selected
                    contentDescription = "$title. $description"
                },
        shape = shapes.control,
        color = if (selected) colors.accentAmberSoft else colors.bgElevated,
        border =
            BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.accentAmber else colors.borderSubtle,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (selected) "● $title" else "○ $title",
                style =
                    typography.controlLabelSmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                color = if (selected) colors.accentAmberBright else colors.contrastWhite,
            )
            Text(
                text = description,
                style = typography.caption,
                color = if (selected) colors.contrastWhite else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ColorAndAutoSplitCard(
    hdrToSdr: Boolean,
    autoSplit: AutoSplitOption,
    onToggleHdrToSdr: (Boolean) -> Unit,
    onSelectAutoSplit: (AutoSplitOption) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography

    DarkroomParameterCard(title = null) {
        ToggleParameterRow(
            label = "Fix Warna HDR10 / Display P3 → sRGB SDR",
            checked = hdrToSdr,
            onCheckedChange = onToggleHdrToSdr,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "AUTO-SPLIT DURASI VIDEO STORY:",
            style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionChip(
                label = "Utuh (Off)",
                selected = autoSplit == AutoSplitOption.OFF,
                onClick = { onSelectAutoSplit(AutoSplitOption.OFF) },
                modifier = Modifier.weight(1f),
            )
            OptionChip(
                label = "Per 30 Detik (WA)",
                selected = autoSplit == AutoSplitOption.EVERY_30S,
                onClick = { onSelectAutoSplit(AutoSplitOption.EVERY_30S) },
                modifier = Modifier.weight(1.2f),
            )
            OptionChip(
                label = "Per 60 Detik (IG)",
                selected = autoSplit == AutoSplitOption.EVERY_60S,
                onClick = { onSelectAutoSplit(AutoSplitOption.EVERY_60S) },
                modifier = Modifier.weight(1.1f),
            )
        }
    }
}

@Composable
private fun DarkroomParameterCard(
    title: String?,
    content: @Composable () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.card,
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = typography.metricMono.copy(fontWeight = FontWeight.Bold),
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .testTag("advanced_option_chip_$label")
                .defaultMinSize(minWidth = ZideeMinTouchTarget, minHeight = ZideeMinTouchTarget)
                .zideeTactileScale(interactionSource)
                .clip(shapes.badge)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                )
                .semantics {
                    this.selected = selected
                    contentDescription = label
                }
                .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .defaultMinSize(minHeight = 36.dp),
            shape = shapes.badge,
            color = if (selected) colors.accentAmberSoft else colors.bgElevated,
            border =
                BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) colors.accentAmber else colors.borderSubtle,
                ),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (selected) "● $label" else label,
                    style =
                        typography.caption.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color = if (selected) colors.accentAmberBright else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SliderHeaderWithReset(
    label: String,
    valueText: String,
    showReset: Boolean,
    resetContentDescription: String,
    onReset: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = typography.controlLabelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.contrastWhite,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = valueText,
                style = typography.metricBadge,
                color = colors.accentAmber,
            )
            if (showReset) {
                IconButton(
                    onClick = onReset,
                    modifier =
                        Modifier
                            .testTag("advanced_slider_reset_$label")
                            .size(ZideeMinTouchTarget),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = resetContentDescription,
                        tint = colors.accentAmberBright,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleParameterRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography

    Row(
        modifier =
            Modifier
                .testTag("advanced_toggle_row_$label")
                .fillMaxWidth()
                .defaultMinSize(minHeight = ZideeMinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = typography.controlLabelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.contrastWhite,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accentAmber,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.bgElevated,
                    uncheckedBorderColor = colors.borderSubtle,
                ),
        )
    }
}

@Composable
private fun zideeSliderColors() =
    SliderDefaults.colors(
        thumbColor = ZideeTheme.colors.contrastWhite,
        activeTrackColor = ZideeTheme.colors.accentAmber,
        inactiveTrackColor = ZideeTheme.colors.bgCanvas,
    )

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10)
@Composable
private fun AdvancedParameterPanelCollapsedPreview() {
    ZideeTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AdvancedParameterPanel(
                expanded = false,
                onToggleExpand = {},
                summaryBadge = "Lanczos3 · USM 28% · x264",
                state = AdvancedTuningUiState(sharpnessPercent = 28),
                onEvent = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, heightDp = 920)
@Composable
private fun AdvancedParameterPanelExpandedPreview() {
    ZideeTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            AdvancedParameterPanel(
                expanded = true,
                onToggleExpand = {},
                summaryBadge = "Lanczos3 · USM 28% · x264",
                state =
                    AdvancedTuningUiState(
                        sharpnessPercent = 28,
                        defaultSharpnessPercent = 25,
                        isManualBitrate = false,
                    ),
                onEvent = {},
            )
        }
    }
}
