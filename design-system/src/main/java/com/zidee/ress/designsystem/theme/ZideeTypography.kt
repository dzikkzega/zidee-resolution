package com.zidee.ress.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography hierarchy for Zidee Ress.
 *
 * All dynamic technical metrics (dimensions, percentages, FPS, bitrate, file sizes)
 * enforce `fontFeatureSettings = "tnum"` so numbers stay spatially locked during slider drags.
 */
@Immutable
data class ZideeTypography(
    val displayTitle: TextStyle =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
    val sectionHeader: TextStyle =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
    val controlLabel: TextStyle =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
    val controlLabelSmall: TextStyle =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
    val metricBadge: TextStyle =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFeatureSettings = "tnum",
        ),
    val metricMono: TextStyle =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFeatureSettings = "tnum",
        ),
    val caption: TextStyle =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        ),
)

val DefaultZideeTypography: ZideeTypography = ZideeTypography()

val LocalZideeTypography = staticCompositionLocalOf { DefaultZideeTypography }
