package com.zidee.ress.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

object ZideeTheme {
    val colors: ZideeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZideeColors.current

    val typography: ZideeTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalZideeTypography.current

    val shapes: ZideeShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalZideeShapes.current
}

private fun ZideeColors.toMaterialDarkScheme(): ColorScheme =
    darkColorScheme(
        primary = accentAmber,
        onPrimary = onAccent,
        primaryContainer = accentAmberSoft,
        onPrimaryContainer = accentAmberBright,
        secondary = contrastWhite,
        onSecondary = onAccent,
        secondaryContainer = bgElevated,
        onSecondaryContainer = contrastWhite,
        tertiary = statusSuccess,
        onTertiary = onAccent,
        background = bgCanvas,
        onBackground = contrastWhite,
        surface = bgSurface,
        onSurface = contrastWhite,
        surfaceVariant = bgElevated,
        onSurfaceVariant = textSecondary,
        outline = borderSubtle,
        outlineVariant = borderCanvasInset,
        error = statusDanger,
        onError = contrastWhite,
        errorContainer = statusDangerSoft,
        onErrorContainer = statusDanger,
    )

private fun ZideeTypography.toMaterialTypography(): Typography =
    Typography(
        headlineSmall = displayTitle,
        titleMedium = sectionHeader,
        labelLarge = controlLabel,
        labelMedium = controlLabelSmall,
        bodyMedium = controlLabel,
        bodySmall = caption,
        labelSmall = metricMono,
    )

private fun ZideeShapes.toMaterialShapes(): Shapes =
    Shapes(
        extraSmall = badge,
        small = controlInner,
        medium = control,
        large = card,
        extraLarge = sheet,
    )

val ZideeMinTouchTarget = 48.dp

/**
 * Root theme provider for Zidee Ress. Enforces the exclusive Pro Darkroom Optics palette.
 */
@Composable
fun ZideeTheme(
    colors: ZideeColors = ProDarkroomColors,
    typography: ZideeTypography = DefaultZideeTypography,
    shapes: ZideeShapes = DefaultZideeShapes,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalZideeColors provides colors,
        LocalZideeTypography provides typography,
        LocalZideeShapes provides shapes,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialDarkScheme(),
            typography = typography.toMaterialTypography(),
            shapes = shapes.toMaterialShapes(),
            content = content,
        )
    }
}
