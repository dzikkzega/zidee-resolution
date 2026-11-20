package com.zidee.ress.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private const val PRESSED_SCALE = 0.97f
private const val PRESS_DURATION_MS = 120

/**
 * Applies the canonical Pro Darkroom tactile press scale (`0.97f`, 120ms ease-out)
 * when the associated [interactionSource] is pressed.
 */
@Composable
fun Modifier.zideeTactileScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) PRESSED_SCALE else 1f,
        animationSpec = tween(durationMillis = PRESS_DURATION_MS, easing = FastOutSlowInEasing),
        label = "ZideeTactileScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
