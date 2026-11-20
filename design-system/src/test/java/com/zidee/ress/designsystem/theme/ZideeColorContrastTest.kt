package com.zidee.ress.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 contrast ratio verification for all Pro Darkroom Optics foreground/background
 * pairs used across Zidee Ress controls and surfaces (T-2.1 acceptance evidence).
 */
class ZideeColorContrastTest {
    private data class ContrastPair(
        val label: String,
        val foreground: Color,
        val background: Color,
        val minRequiredRatio: Double,
    )

    @Test
    fun `all normal text and icon pairs meet WCAG 2_1 AA contrast threshold`() {
        val c = ProDarkroomColors
        val amberSoftOnSurface = compositeOver(c.accentAmberSoft, c.bgSurface)
        val amberSoftOnElevated = compositeOver(c.accentAmberSoft, c.bgElevated)
        val glassDarkOnCanvas = compositeOver(c.bgGlassDark, c.bgCanvas)
        val glassDarkOnSurface = compositeOver(c.bgGlassDark, c.bgSurface)

        val pairs =
            listOf(
                ContrastPair("contrastWhite on bgCanvas", c.contrastWhite, c.bgCanvas, WCAG_AA_NORMAL_TEXT),
                ContrastPair("contrastWhite on bgSurface", c.contrastWhite, c.bgSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("contrastWhite on bgElevated", c.contrastWhite, c.bgElevated, WCAG_AA_NORMAL_TEXT),
                ContrastPair("contrastWhite on bgGlassDark(bgSurface)", c.contrastWhite, glassDarkOnSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("contrastWhite on accentAmberSoft(bgSurface)", c.contrastWhite, amberSoftOnSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("textSecondary on bgCanvas", c.textSecondary, c.bgCanvas, WCAG_AA_NORMAL_TEXT),
                ContrastPair("textSecondary on bgSurface", c.textSecondary, c.bgSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("textSecondary on bgElevated", c.textSecondary, c.bgElevated, WCAG_AA_NORMAL_TEXT),
                ContrastPair("textSecondary on bgGlassDark(bgCanvas)", c.textSecondary, glassDarkOnCanvas, WCAG_AA_NORMAL_TEXT),
                ContrastPair("accentAmber on bgCanvas", c.accentAmber, c.bgCanvas, WCAG_AA_NORMAL_TEXT),
                ContrastPair("accentAmber on bgSurface", c.accentAmber, c.bgSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("accentAmber on bgElevated", c.accentAmber, c.bgElevated, WCAG_AA_NORMAL_TEXT),
                ContrastPair("accentAmberBright on bgCanvas", c.accentAmberBright, c.bgCanvas, WCAG_AA_NORMAL_TEXT),
                ContrastPair("accentAmberBright on bgSurface", c.accentAmberBright, c.bgSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("accentAmberBright on bgElevated", c.accentAmberBright, c.bgElevated, WCAG_AA_NORMAL_TEXT),
                ContrastPair(
                    "accentAmberBright on accentAmberSoft(bgSurface)",
                    c.accentAmberBright,
                    amberSoftOnSurface,
                    WCAG_AA_NORMAL_TEXT,
                ),
                ContrastPair(
                    "accentAmberBright on accentAmberSoft(bgElevated)",
                    c.accentAmberBright,
                    amberSoftOnElevated,
                    WCAG_AA_NORMAL_TEXT,
                ),
                ContrastPair("accentAmberBright on bgGlassDark(bgSurface)", c.accentAmberBright, glassDarkOnSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("onAccent on accentAmber (active pill & handle)", c.onAccent, c.accentAmber, WCAG_AA_NORMAL_TEXT),
                ContrastPair("statusSuccess on bgCanvas", c.statusSuccess, c.bgCanvas, WCAG_AA_NORMAL_TEXT),
                ContrastPair("statusSuccess on bgSurface", c.statusSuccess, c.bgSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("statusSuccess on bgElevated", c.statusSuccess, c.bgElevated, WCAG_AA_NORMAL_TEXT),
                ContrastPair("statusWarning on bgCanvas", c.statusWarning, c.bgCanvas, WCAG_AA_NORMAL_TEXT),
                ContrastPair("statusWarning on bgSurface", c.statusWarning, c.bgSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("statusDanger on bgCanvas", c.statusDanger, c.bgCanvas, WCAG_AA_NORMAL_TEXT),
                ContrastPair("statusDanger on bgSurface", c.statusDanger, c.bgSurface, WCAG_AA_NORMAL_TEXT),
                ContrastPair("textMuted non-text UI indicator on bgSurface", c.textMuted, c.bgSurface, WCAG_AA_NON_TEXT_UI),
                ContrastPair("textMuted non-text UI indicator on bgElevated", c.textMuted, c.bgElevated, WCAG_AA_NON_TEXT_UI),
            )

        val report = StringBuilder("WCAG 2.1 Measured Contrast Ratios (ProDarkroomColors):\n")
        pairs.forEach { pair ->
            val ratio = wcagContrastRatio(pair.foreground, pair.background)
            val line =
                String.format(
                    java.util.Locale.US,
                    "  - %-50s = %5.2f:1 (min %.1f:1)",
                    pair.label,
                    ratio,
                    pair.minRequiredRatio,
                )
            report.appendLine(line)
            assertTrue(
                "Contrast ratio for '${pair.label}' was %.2f:1, below WCAG minimum %.1f:1"
                    .format(java.util.Locale.US, ratio, pair.minRequiredRatio),
                ratio >= pair.minRequiredRatio,
            )
        }
        println(report.toString())
    }

    companion object {
        private const val WCAG_AA_NORMAL_TEXT = 4.5
        private const val WCAG_AA_NON_TEXT_UI = 3.0

        private fun compositeOver(
            foreground: Color,
            background: Color,
        ): Color {
            val fgAlpha = foreground.alpha
            val invAlpha = 1f - fgAlpha
            return Color(
                red = foreground.red * fgAlpha + background.red * invAlpha,
                green = foreground.green * fgAlpha + background.green * invAlpha,
                blue = foreground.blue * fgAlpha + background.blue * invAlpha,
                alpha = 1f,
            )
        }

        private fun wcagContrastRatio(
            foreground: Color,
            background: Color,
        ): Double {
            val resolvedFg = if (foreground.alpha < 1f) compositeOver(foreground, background) else foreground
            val l1 = relativeLuminance(resolvedFg)
            val l2 = relativeLuminance(background)
            val lighter = max(l1, l2)
            val darker = min(l1, l2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        private fun relativeLuminance(color: Color): Double {
            val r = linearizeChannel(color.red.toDouble())
            val g = linearizeChannel(color.green.toDouble())
            val b = linearizeChannel(color.blue.toDouble())
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        private fun linearizeChannel(channel: Double): Double =
            if (channel <= 0.03928) {
                channel / 12.92
            } else {
                ((channel + 0.055) / 1.055).pow(2.4)
            }
    }
}
