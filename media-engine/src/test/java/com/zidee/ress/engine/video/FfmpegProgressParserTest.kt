package com.zidee.ress.engine.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegProgressParserTest {
    @Test
    fun `parses timeMs into clamped fraction`() {
        assertEquals(0.5f, FfmpegProgressParser.fractionFromElapsedMs(15_000.0, 30_000L) ?: -1f, 1e-4f)
        assertEquals(1.0f, FfmpegProgressParser.fractionFromElapsedMs(40_000.0, 30_000L) ?: -1f, 1e-4f)
        assertEquals(0.0f, FfmpegProgressParser.fractionFromElapsedMs(-100.0, 30_000L) ?: -1f, 1e-4f)
    }

    @Test
    fun `returns null when duration is missing or non positive`() {
        assertNull(FfmpegProgressParser.fractionFromElapsedMs(5_000.0, null))
        assertNull(FfmpegProgressParser.fractionFromElapsedMs(5_000.0, 0L))
    }

    @Test
    fun `parses ffmpeg out_time_ms microseconds line`() {
        val fraction = FfmpegProgressParser.parseLogLine("out_time_ms=15000000", totalDurationMs = 30_000L)
        assertEquals(0.5f, fraction ?: -1f, 1e-4f)
    }

    @Test
    fun `parses ffmpeg time=HH_MM_SS_xx log line`() {
        val fraction =
            FfmpegProgressParser.parseLogLine(
                "frame= 450 fps= 29 q=21.0 size= 1280kB time=00:00:15.00 bitrate= 699.0kbits/s speed=1.2x",
                totalDurationMs = 30_000L,
            )
        assertEquals(0.5f, fraction ?: -1f, 1e-4f)
    }
}
