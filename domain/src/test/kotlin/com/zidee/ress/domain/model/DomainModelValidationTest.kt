package com.zidee.ress.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelValidationTest {
    @Test
    fun `display size swaps dimensions for 90 and 270 degree rotation`() {
        assertEquals(PixelSize(1080, 1920), metadata(rotation = 90).displaySize)
        assertEquals(PixelSize(1080, 1920), metadata(rotation = 270).displaySize)
        assertEquals(PixelSize(1080, 1920), metadata(rotation = -90).displaySize)
        assertEquals(PixelSize(1920, 1080), metadata(rotation = 180).displaySize)
        assertEquals(PixelSize(1920, 1080), metadata(rotation = 0).displaySize)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `metadata rejects non positive dimensions`() {
        metadata(width = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `metadata rejects rotation that is not a right angle`() {
        metadata(rotation = 45)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `media uri rejects blank value`() {
        MediaUri(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `crop zoom below 1 is rejected`() {
        CropAdjustment(zoom = 0.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `crop pan outside 0 to 1 is rejected`() {
        CropAdjustment(panX = 1.2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `jpeg quality below 80 is rejected`() {
        PhotoAdvancedConfig(jpegQuality = 79)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sharpness above 100 is rejected`() {
        PhotoAdvancedConfig(sharpnessPercent = 101)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `manual bitrate outside 1_5 to 12 Mbps is rejected`() {
        BitrateMode.Manual(1_000)
    }

    @Test
    fun `manual bitrate accepts range bounds`() {
        assertEquals(1_500, BitrateMode.Manual(1_500).kbps)
        assertEquals(12_000, BitrateMode.Manual(12_000).kbps)
    }

    @Test
    fun `blur background geometry accepts range bounds 10 to 60 px`() {
        val min = CanvasGeometry.BlurBackground(PixelRect(0, 0, 1080, 608), blurRadiusPx = 10)
        val max = CanvasGeometry.BlurBackground(PixelRect(0, 0, 1080, 608), blurRadiusPx = 60)
        assertEquals(10, min.blurRadiusPx)
        assertEquals(60, max.blurRadiusPx)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blur background geometry rejects radius below 10 px`() {
        CanvasGeometry.BlurBackground(PixelRect(0, 0, 1080, 608), blurRadiusPx = 9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blur background geometry rejects radius above 60 px`() {
        CanvasGeometry.BlurBackground(PixelRect(0, 0, 1080, 608), blurRadiusPx = 61)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `optimization request rejects out of range blur radius`() {
        OptimizationRequest(
            source = metadata(),
            platform = TargetPlatform.WA_STORY,
            aspectMode = CanvasAspectMode.BLUR_BG_9_16,
            blurRadiusPx = 70,
        )
    }

    @Test
    fun `video defaults enable platform auto split only on stories`() {
        assertEquals(AutoSplitMode.EVERY_30S, VideoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY).autoSplit)
        assertEquals(AutoSplitMode.EVERY_60S, VideoAdvancedConfig.defaultFor(TargetPlatform.IG_STORY).autoSplit)
        assertEquals(AutoSplitMode.OFF, VideoAdvancedConfig.defaultFor(TargetPlatform.IG_POST).autoSplit)
    }

    @Test
    fun `album paths point at ZideeResolution`() {
        assertEquals("Pictures/ZideeResolution", ZideeAlbum.relativePathFor(MediaType.PHOTO))
        assertEquals("Movies/ZideeResolution", ZideeAlbum.relativePathFor(MediaType.VIDEO))
    }

    @Test
    fun `pixel size helpers`() {
        val size = PixelSize(1080, 1920)
        assertTrue(size.isPortrait)
        assertFalse(PixelSize(10, 10).isPortrait)
        assertEquals(2_073_600L, size.pixelCount)
        assertEquals("1080x1920", size.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `optimized item part index must be within total parts`() {
        OptimizedMediaItem(
            id = "a",
            mediaStoreUri = MediaUri("content://media/1"),
            displayName = "ZIDEE_VID.mp4",
            mediaType = MediaType.VIDEO,
            platform = TargetPlatform.WA_STORY,
            aspectMode = CanvasAspectMode.ORIGINAL,
            originalSize = PixelSize(1920, 1080),
            outputSize = PixelSize(1080, 608),
            originalSizeBytes = 10,
            outputSizeBytes = 5,
            durationMs = 30_000,
            partIndex = 3,
            totalParts = 2,
            createdAtEpochMs = 0,
        )
    }

    private fun metadata(
        width: Int = 1920,
        height: Int = 1080,
        rotation: Int = 0,
    ) = MediaMetadata(
        uri = MediaUri("content://test/1"),
        mediaType = MediaType.VIDEO,
        width = width,
        height = height,
        sizeBytes = 1,
        rotationDegrees = rotation,
    )
}
