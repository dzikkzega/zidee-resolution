package com.zidee.ress.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * Horizontal or wrapping 5-platform selector pills (`WA Story`, `WA Chat`, `IG Story`, `IG Post`, `DM`).
 * Each pill guarantees a `>= 48dp` touch target while preserving the compact 34dp visual silhouette.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformPresetSelector(
    options: List<PlatformPresetOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    wrapItems: Boolean = false,
) {
    if (wrapItems) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                PlatformPresetPill(
                    option = option,
                    isSelected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                )
            }
        }
    } else {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                PlatformPresetPill(
                    option = option,
                    isSelected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                )
            }
        }
    }
}

@Composable
private fun PlatformPresetPill(
    option: PlatformPresetOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentAmber else colors.bgElevated,
        animationSpec = tween(durationMillis = 150),
        label = "PlatformPillBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.onAccent else colors.contrastWhite,
        animationSpec = tween(durationMillis = 150),
        label = "PlatformPillText",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentAmber else colors.borderSubtle,
        animationSpec = tween(durationMillis = 150),
        label = "PlatformPillBorder",
    )

    Box(
        modifier =
            Modifier
                .testTag("platform_preset_pill_${option.id}")
                .defaultMinSize(minWidth = ZideeMinTouchTarget, minHeight = ZideeMinTouchTarget)
                .zideeTactileScale(interactionSource = interactionSource)
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
                    selected = isSelected
                    contentDescription = option.contentDescription
                }
                .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.height(34.dp),
            shape = shapes.control,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isSelected) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.onAccent),
                    )
                }
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

@Preview(showBackground = true, backgroundColor = 0xFF13171D)
@Composable
private fun PlatformPresetSelectorPreview() {
    val sampleOptions =
        listOf(
            PlatformPresetOption(id = "WA_STORY", label = "WA Story"),
            PlatformPresetOption(id = "WA_CHAT", label = "WA Chat"),
            PlatformPresetOption(id = "IG_STORY", label = "IG Story"),
            PlatformPresetOption(id = "IG_POST", label = "IG Post"),
            PlatformPresetOption(id = "IG_CHAT", label = "DM"),
        )
    ZideeTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            PlatformPresetSelector(
                options = sampleOptions,
                selectedId = "WA_STORY",
                onSelect = {},
            )
        }
    }
}
