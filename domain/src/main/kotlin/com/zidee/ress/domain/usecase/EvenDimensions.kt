package com.zidee.ress.domain.usecase

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Modulo-2 helpers. H.264 / YUV420p needs even width and height, so every dimension the
 * domain emits passes through here.
 */
object EvenDimensions {
    private const val MIN_DIMENSION = 2

    /** `max(2, round(x / 2) * 2)` as defined in MEDIA_ENGINE_SPEC §1. */
    fun roundEven(x: Double): Int = max(MIN_DIMENSION, (x / 2.0).roundToInt() * 2)

    /** Largest even value not above [x], never below 2. Used to avoid stretching past the source. */
    fun floorEven(x: Int): Int = max(MIN_DIMENSION, x - x % 2)

    /** Nearest even offset (may be 0). Keeps crop origins chroma-aligned. */
    internal fun roundEvenOffset(x: Double): Int = (x / 2.0).roundToInt() * 2
}
