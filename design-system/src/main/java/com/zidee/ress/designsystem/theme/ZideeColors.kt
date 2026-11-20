package com.zidee.ress.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Exclusive Pro Darkroom Optics color palette for Zidee Ress.
 *
 * Neutral obsidian/carbon surfaces prevent chromatic adaptation so the user can accurately
 * judge sharpness, tone-mapping, and compression artifacts across media previews.
 */
@Immutable
data class ZideeColors(
    val bgCanvas: Color = Color(0xFF0B0D10),
    val bgSurface: Color = Color(0xFF13171D),
    val bgElevated: Color = Color(0xFF1C222B),
    val bgGlass: Color = Color(0xCC13171D),
    val bgGlassDark: Color = Color(0xCC0B0D10),
    val accentAmber: Color = Color(0xFFF59E0B),
    val accentAmberSoft: Color = Color(0x24F59E0B),
    val accentAmberBright: Color = Color(0xFFFBBF24),
    val contrastWhite: Color = Color(0xFFF8FAFC),
    val textSecondary: Color = Color(0xFF94A3B8),
    val textMuted: Color = Color(0xFF64748B),
    val borderSubtle: Color = Color(0x14FFFFFF),
    val borderCanvasInset: Color = Color(0x1AFFFFFF),
    val borderActive: Color = Color(0x80F59E0B),
    val statusSuccess: Color = Color(0xFF10B981),
    val statusSuccessSoft: Color = Color(0x1F10B981),
    val statusWarning: Color = Color(0xFFF97316),
    val statusDanger: Color = Color(0xFFEF4444),
    val statusDangerSoft: Color = Color(0x1FEF4444),
    val onAccent: Color = Color(0xFF0B0D10),
)

val ProDarkroomColors: ZideeColors = ZideeColors()

val LocalZideeColors = staticCompositionLocalOf { ProDarkroomColors }
