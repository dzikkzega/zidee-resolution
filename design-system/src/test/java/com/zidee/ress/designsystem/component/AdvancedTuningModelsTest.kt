package com.zidee.ress.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedTuningModelsTest {
    @Test
    fun `clamping enforces Pro Darkroom tuning bounds`() {
        assertEquals(0, AdvancedTuningUiState.clampSharpness(-10))
        assertEquals(100, AdvancedTuningUiState.clampSharpness(150))
        assertEquals(28, AdvancedTuningUiState.clampSharpness(28))

        assertEquals(80, AdvancedTuningUiState.clampJpegQuality(50))
        assertEquals(100, AdvancedTuningUiState.clampJpegQuality(110))
        assertEquals(94, AdvancedTuningUiState.clampJpegQuality(94))

        assertEquals(1_500, AdvancedTuningUiState.clampManualBitrateKbps(500))
        assertEquals(12_000, AdvancedTuningUiState.clampManualBitrateKbps(20_000))
        assertEquals(4_000, AdvancedTuningUiState.clampManualBitrateKbps(4_000))
    }

    @Test
    fun `modified flags reflect difference from preset defaults`() {
        val defaultState =
            AdvancedTuningUiState(
                sharpnessPercent = 25,
                defaultSharpnessPercent = 25,
                jpegQuality = 94,
                defaultJpegQuality = 94,
            )
        assertFalse(defaultState.isSharpnessModified)
        assertFalse(defaultState.isJpegQualityModified)

        val editedState = defaultState.copy(sharpnessPercent = 42, jpegQuality = 96)
        assertTrue(editedState.isSharpnessModified)
        assertTrue(editedState.isJpegQualityModified)
        assertEquals("3.5 Mbps", editedState.formatBitrateMbps())
    }
}
