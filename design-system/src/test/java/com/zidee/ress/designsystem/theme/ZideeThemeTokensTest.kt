package com.zidee.ress.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zidee.ress.designsystem.component.SplitSliderSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class ZideeThemeTokensTest {
    @Test
    fun `ProDarkroomColors match DESIGN md hex contract`() {
        assertEquals(Color(0xFF0B0D10), ProDarkroomColors.bgCanvas)
        assertEquals(Color(0xFF13171D), ProDarkroomColors.bgSurface)
        assertEquals(Color(0xFF1C222B), ProDarkroomColors.bgElevated)
        assertEquals(Color(0xFFF59E0B), ProDarkroomColors.accentAmber)
        assertEquals(Color(0xFFFBBF24), ProDarkroomColors.accentAmberBright)
        assertEquals(Color(0xFFF8FAFC), ProDarkroomColors.contrastWhite)
        assertEquals(Color(0xFF10B981), ProDarkroomColors.statusSuccess)
    }

    @Test
    fun `metric typography styles enforce tabular numerals and minimum touch target is 48dp`() {
        assertEquals("tnum", DefaultZideeTypography.metricBadge.fontFeatureSettings)
        assertEquals("tnum", DefaultZideeTypography.metricMono.fontFeatureSettings)
        assertEquals(48.dp, ZideeMinTouchTarget)
    }

    @Test
    fun `split slider visual handle and divider line match FigCraft frame 119 spec`() {
        assertEquals(40.dp, SplitSliderSpec.HandleVisualSize)
        assertEquals(2.dp, SplitSliderSpec.DividerLineWidth)
    }
}
