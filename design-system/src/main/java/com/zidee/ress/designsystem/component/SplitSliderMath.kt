package com.zidee.ress.designsystem.component

import kotlin.math.abs

/** Pure math helpers for [BeforeAfterSplitSlider] drag and center-detent behavior. */
object SplitSliderMath {
    const val CENTER_FRACTION: Float = 0.5f
    const val DEFAULT_DETENT_THRESHOLD: Float = 0.018f

    fun clampFraction(fraction: Float): Float = if (fraction.isNaN()) CENTER_FRACTION else fraction.coerceIn(0f, 1f)

    fun fractionFromPointerX(
        pointerX: Float,
        widthPx: Float,
    ): Float {
        if (widthPx <= 0f) return CENTER_FRACTION
        return clampFraction(pointerX / widthPx)
    }

    fun applyCenterDetent(
        fraction: Float,
        threshold: Float = DEFAULT_DETENT_THRESHOLD,
    ): Float {
        val clamped = clampFraction(fraction)
        return if (abs(clamped - CENTER_FRACTION) <= threshold) CENTER_FRACTION else clamped
    }

    fun didCrossCenter(
        previous: Float,
        current: Float,
    ): Boolean {
        val prevDelta = previous - CENTER_FRACTION
        val currDelta = current - CENTER_FRACTION
        return (prevDelta < 0f && currDelta >= 0f) || (prevDelta > 0f && currDelta <= 0f)
    }
}
