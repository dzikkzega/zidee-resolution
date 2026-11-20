package com.zidee.ress.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

enum class CanvasModeIconKind {
    ORIGINAL,
    BLUR_BG_9_16,
    CROP_9_16,
}

@Immutable
data class CanvasModeOption(
    val id: String,
    val label: String,
    val iconKind: CanvasModeIconKind,
    val contentDescription: String = label,
)

/**
 * Concentric 3-mode canvas selector (`Rasio Asli` | `Blur BG 9:16` | `Crop 9:16`).
 * Outer track (`12dp` radius) wraps inner active indicator (`8dp` radius) with `4dp` visual inset
 * while guaranteeing each clickable segment meets the `>= 48dp` minimum touch target.
 */
@Composable
fun CanvasModeSegmentedControl(
    options: List<CanvasModeOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val shapes = ZideeTheme.shapes

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ZideeMinTouchTarget)
                .clip(shapes.control)
                .background(colors.bgCanvas)
                .border(width = 1.dp, color = colors.borderSubtle, shape = shapes.control)
                .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            CanvasModeSegmentItem(
                option = option,
                isSelected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CanvasModeSegmentItem(
    option: CanvasModeOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.bgElevated else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "CanvasModeBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentAmberBright else colors.textSecondary,
        animationSpec = tween(durationMillis = 150),
        label = "CanvasModeText",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.borderActive else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "CanvasModeBorder",
    )

    Box(
        modifier =
            modifier
                .testTag("canvas_mode_segment_${option.id}")
                .defaultMinSize(minWidth = ZideeMinTouchTarget, minHeight = ZideeMinTouchTarget)
                .zideeTactileScale(interactionSource = interactionSource)
                .clip(shapes.controlInner)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                )
                .semantics {
                    selected = isSelected
                    contentDescription = option.contentDescription
                }
                .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp),
            shape = shapes.controlInner,
            color = backgroundColor,
            border = if (isSelected) BorderStroke(1.5.dp, borderColor) else null,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = option.iconKind.toImageVector(),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = option.label,
                    style =
                        typography.controlLabelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun CanvasModeIconKind.toImageVector(): ImageVector =
    when (this) {
        CanvasModeIconKind.ORIGINAL -> Icons.Rounded.CropFree
        CanvasModeIconKind.BLUR_BG_9_16 -> Icons.Rounded.AutoAwesome
        CanvasModeIconKind.CROP_9_16 -> Icons.Rounded.CropPortrait
    }

@Preview(showBackground = true, backgroundColor = 0xFF13171D)
@Composable
private fun CanvasModeSegmentedControlPreview() {
    val options =
        listOf(
            CanvasModeOption("ORIGINAL", "Rasio Asli", CanvasModeIconKind.ORIGINAL),
            CanvasModeOption("BLUR_BG_9_16", "Blur BG 9:16", CanvasModeIconKind.BLUR_BG_9_16),
            CanvasModeOption("CROP_9_16", "Crop 9:16", CanvasModeIconKind.CROP_9_16),
        )
    ZideeTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CanvasModeSegmentedControl(
                options = options,
                selectedId = "BLUR_BG_9_16",
                onSelect = {},
            )
        }
    }
}
