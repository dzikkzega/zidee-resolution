package com.zidee.ress.core.format

import java.util.Locale

/** Deterministic UI-agnostic formatters for media file sizes, durations, and dimensions. */
object MediaFormatters {
    private const val KILOBYTE = 1_024.0
    private const val MEGABYTE = KILOBYTE * 1_024.0
    private const val GIGABYTE = MEGABYTE * 1_024.0
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < KILOBYTE -> "$safe B"
            safe < MEGABYTE -> String.format(Locale.US, "%.0f KB", safe / KILOBYTE)
            safe < GIGABYTE -> String.format(Locale.US, "%.2f MB", safe / MEGABYTE)
            else -> String.format(Locale.US, "%.2f GB", safe / GIGABYTE)
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) + MS_PER_SECOND / 2) / MS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatResolution(
        width: Int,
        height: Int,
    ): String = "$width × $height"
}
