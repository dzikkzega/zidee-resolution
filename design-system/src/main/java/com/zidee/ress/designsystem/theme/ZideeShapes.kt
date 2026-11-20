package com.zidee.ress.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Concentric corner radii system following optical nesting alignment (`DESIGN.md` section 5):
 * Sheet (28dp) -> Card (20dp) -> Control/Button (12dp) -> Inner Segment/Badge (8dp).
 */
@Immutable
data class ZideeShapes(
    val sheet: CornerBasedShape = RoundedCornerShape(28.dp),
    val sheetTop: CornerBasedShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val card: CornerBasedShape = RoundedCornerShape(20.dp),
    val canvasFrame: CornerBasedShape = RoundedCornerShape(18.dp),
    val control: CornerBasedShape = RoundedCornerShape(12.dp),
    val controlInner: CornerBasedShape = RoundedCornerShape(8.dp),
    val badge: CornerBasedShape = RoundedCornerShape(8.dp),
    val pill: CornerBasedShape = RoundedCornerShape(50),
)

val DefaultZideeShapes: ZideeShapes = ZideeShapes()

val LocalZideeShapes = staticCompositionLocalOf { DefaultZideeShapes }
