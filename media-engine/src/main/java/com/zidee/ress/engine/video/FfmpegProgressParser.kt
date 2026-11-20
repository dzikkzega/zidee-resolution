package com.zidee.ress.engine.video

/**
 * Converts FFmpegKit statistics timestamps (`timeMs`) or FFmpeg log lines
 * (`out_time_ms=...` / `time=HH:MM:SS.xx`) into a clamped progress fraction `0f..1f`.
 */
object FfmpegProgressParser {
    private val OUT_TIME_US_REGEX = Regex("""out_time_ms=(\d+)""")
    private val TIME_CODE_REGEX = Regex("""time=(\d+):(\d{2}):(\d{2}(?:\.\d+)?)""")

    fun fractionFromElapsedMs(
        elapsedMs: Double,
        totalDurationMs: Long?,
    ): Float? {
        if (totalDurationMs == null || totalDurationMs <= 0L) return null
        return (elapsedMs / totalDurationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun parseLogLine(
        line: String,
        totalDurationMs: Long?,
    ): Float? {
        val elapsedMs = parseOutTimeElapsedMs(line) ?: parseTimeCodeElapsedMs(line) ?: return null
        return fractionFromElapsedMs(elapsedMs, totalDurationMs)
    }

    private fun parseOutTimeElapsedMs(line: String): Double? {
        val micros = OUT_TIME_US_REGEX.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return micros / 1000.0
    }

    private fun parseTimeCodeElapsedMs(line: String): Double? {
        val match = TIME_CODE_REGEX.find(line) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toDoubleOrNull() ?: return null
        return ((hours * 3600L + minutes * 60L) * 1000.0) + seconds * 1000.0
    }
}
