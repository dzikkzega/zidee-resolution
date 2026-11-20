package com.zidee.ress.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitSliderMathTest {
    @Test
    fun `clampFraction bounds out of range and NaN values`() {
        assertEquals(0f, SplitSliderMath.clampFraction(-0.4f), 0.0001f)
        assertEquals(1f, SplitSliderMath.clampFraction(1.4f), 0.0001f)
        assertEquals(SplitSliderMath.CENTER_FRACTION, SplitSliderMath.clampFraction(Float.NaN), 0.0001f)
        assertEquals(0.32f, SplitSliderMath.clampFraction(0.32f), 0.0001f)
    }

    @Test
    fun `fractionFromPointerX handles zero width safely`() {
        assertEquals(0.5f, SplitSliderMath.fractionFromPointerX(120f, 0f), 0.0001f)
        assertEquals(0.5f, SplitSliderMath.fractionFromPointerX(120f, -10f), 0.0001f)
        assertEquals(0.25f, SplitSliderMath.fractionFromPointerX(100f, 400f), 0.0001f)
        assertEquals(1f, SplitSliderMath.fractionFromPointerX(500f, 400f), 0.0001f)
    }

    @Test
    fun `applyCenterDetent snaps within threshold and preserves outside`() {
        assertEquals(0.5f, SplitSliderMath.applyCenterDetent(0.51f), 0.0001f)
        assertEquals(0.5f, SplitSliderMath.applyCenterDetent(0.49f), 0.0001f)
        assertEquals(0.62f, SplitSliderMath.applyCenterDetent(0.62f), 0.0001f)
        assertEquals(0.35f, SplitSliderMath.applyCenterDetent(0.35f), 0.0001f)
    }

    @Test
    fun `didCrossCenter detects crossing across midpoint in either direction`() {
        assertTrue(SplitSliderMath.didCrossCenter(0.45f, 0.50f))
        assertTrue(SplitSliderMath.didCrossCenter(0.45f, 0.55f))
        assertTrue(SplitSliderMath.didCrossCenter(0.55f, 0.45f))
        assertFalse(SplitSliderMath.didCrossCenter(0.20f, 0.35f))
        assertFalse(SplitSliderMath.didCrossCenter(0.50f, 0.60f))
    }
}
