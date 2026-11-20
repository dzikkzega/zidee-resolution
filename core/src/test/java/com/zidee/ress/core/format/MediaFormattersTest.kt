package com.zidee.ress.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFormattersTest {
    @Test
    fun `formatBytes formats bytes, kilobytes, megabytes, and gigabytes`() {
        assertEquals("0 B", MediaFormatters.formatBytes(-5))
        assertEquals("512 B", MediaFormatters.formatBytes(512))
        assertEquals("100 KB", MediaFormatters.formatBytes(102_400))
        assertEquals("2.50 MB", MediaFormatters.formatBytes(2_621_440))
        assertEquals("1.50 GB", MediaFormatters.formatBytes(1_610_612_736L))
    }

    @Test
    fun `formatDuration formats MM_SS and H_MM_SS cleanly`() {
        assertEquals("00:00", MediaFormatters.formatDuration(0))
        assertEquals("00:30", MediaFormatters.formatDuration(30_000))
        assertEquals("01:15", MediaFormatters.formatDuration(75_000))
        assertEquals("1:01:05", MediaFormatters.formatDuration(3_665_000))
    }

    @Test
    fun `formatResolution displays width by height`() {
        assertEquals("1080 × 1920", MediaFormatters.formatResolution(1080, 1920))
    }
}
