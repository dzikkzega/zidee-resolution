package com.zidee.ress.presentation.common

import com.zidee.ress.designsystem.component.AdvancedTuningEvent
import com.zidee.ress.designsystem.component.AutoSplitOption
import com.zidee.ress.designsystem.component.EncoderOption
import com.zidee.ress.designsystem.component.FrameRateOption
import com.zidee.ress.designsystem.component.MediaTuningMode
import com.zidee.ress.designsystem.component.ResamplingOption
import com.zidee.ress.designsystem.component.VideoResolutionOption
import com.zidee.ress.domain.model.AutoSplitMode
import com.zidee.ress.domain.model.BitrateMode
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.FrameRateMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.PhotoAdvancedConfig
import com.zidee.ress.domain.model.PhotoEncodingSpec
import com.zidee.ress.domain.model.PhotoTargetResolution
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.ScalingAlgorithm
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoAdvancedConfig
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.model.VideoEncodingSpec
import com.zidee.ress.domain.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUiMappersTest {
    @Test
    fun `platform defaults expose all 5 targets and 3 canvas modes`() {
        assertEquals(5, ZideePresentationDefaults.platformOptions.size)
        assertEquals(3, ZideePresentationDefaults.canvasModeOptions.size)

        assertEquals("WA", TargetPlatform.WA_STORY.toActionTargetShort())
        assertEquals("WA", TargetPlatform.WA_CHAT.toActionTargetShort())
        assertEquals("IG", TargetPlatform.IG_STORY.toActionTargetShort())
        assertEquals("IG", TargetPlatform.IG_POST.toActionTargetShort())
        assertEquals("IG", TargetPlatform.IG_CHAT.toActionTargetShort())

        assertTrue(TargetPlatform.WA_STORY.toDirectPackageHint().contains("com.whatsapp"))
        assertTrue(TargetPlatform.IG_POST.toDirectPackageHint().contains("com.instagram.android"))
    }

    @Test
    fun `spec and summary formatters emit deterministic tabular strings`() {
        val meta =
            MediaMetadata(
                uri = MediaUri("content://media/external/video/media/99"),
                mediaType = MediaType.VIDEO,
                width = 3840,
                height = 2160,
                sizeBytes = 64L * 1024L * 1024L,
                durationMs = 45_000L,
                frameRate = 60.0,
                isHdr = true,
            )
        val spec =
            OptimalMediaSpec(
                platform = TargetPlatform.WA_STORY,
                aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                mediaType = MediaType.VIDEO,
                sourceSize = PixelSize(3840, 2160),
                outputSize = PixelSize(1080, 1920),
                geometry = CanvasGeometry.BlurBackground(PixelRect(0, 656, 1080, 608), blurRadiusPx = 42),
                photo = PhotoEncodingSpec(ScalingAlgorithm.LANCZOS3, 28, 0.42, 94, true),
                video =
                    VideoEncodingSpec(
                        frameRate = 30.0,
                        encoder = VideoEncoder.CPU_LIBX264,
                        maxBitrateKbps = 2_400,
                        bufferSizeKbps = 4_800,
                        audioBitrateKbps = 128,
                        audioSampleRateHz = 44_100,
                        sharpen = true,
                        applyHdrToSdr = true,
                        segmentSeconds = 30,
                        segmentDurationsMs = listOf(30_000L, 15_000L),
                    ),
                estimatedSizeBytes = 7_400_000L,
            )

        val comparison = meta.toSourceSpecComparisonLine(spec)
        assertTrue(comparison.contains("ASLI: 3840 × 2160 60fps"))
        assertTrue(comparison.contains("TARGET: 1080 × 1920 30fps"))

        assertTrue(meta.toAsliSummaryLine().contains("HDR"))
        assertTrue(spec.toTargetSummaryLine().contains("1080 × 1920"))
        assertEquals("Kanvas 9:16 · Blur BG Radius: 42px", spec.toCanvasStatusPill(CanvasAspectMode.BLUR_BG_9_16))
        assertEquals(
            "Kanvas 9:16 · Blur BG Radius: 48px",
            spec.toCanvasStatusPill(CanvasAspectMode.BLUR_BG_9_16, blurRadiusPx = 48),
        )
        assertTrue(MediaType.PHOTO.toDestinationAlbumHint().contains("Pictures/ZideeResolution"))
        assertTrue(MediaType.VIDEO.toDestinationAlbumHint().contains("Movies/ZideeResolution"))
    }

    @Test
    fun `advanced tuning mapper maps and dispatches photo and video configs accurately`() {
        var photo = PhotoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY)
        var video = VideoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY)

        fun dispatch(event: AdvancedTuningEvent) {
            dispatchAdvancedTuningEvent(
                event = event,
                platform = TargetPlatform.WA_STORY,
                onUpdatePhoto = { photo = it(photo) },
                onUpdateVideo = { video = it(video) },
            )
        }

        dispatch(AdvancedTuningEvent.SelectScaling(ResamplingOption.BICUBIC))
        dispatch(AdvancedTuningEvent.ChangeSharpness(45))
        dispatch(AdvancedTuningEvent.SelectPhotoResolution(1600))
        dispatch(AdvancedTuningEvent.ChangeJpegQuality(90))
        dispatch(AdvancedTuningEvent.ToggleAutoSrgbFix(false))
        dispatch(AdvancedTuningEvent.SelectVideoResolution(VideoResolutionOption.HD_720P_ULTRA_SHARP))
        dispatch(AdvancedTuningEvent.SelectFrameRate(FrameRateOption.FPS_60))
        dispatch(AdvancedTuningEvent.SelectEncoder(EncoderOption.GPU_MEDIACODEC))
        dispatch(AdvancedTuningEvent.ToggleManualBitrate(true))
        dispatch(AdvancedTuningEvent.ChangeManualBitrateKbps(4_200))
        dispatch(AdvancedTuningEvent.ToggleHdrToSdr(false))
        dispatch(AdvancedTuningEvent.SelectAutoSplit(AutoSplitOption.EVERY_60S))

        assertEquals(ScalingAlgorithm.BICUBIC, photo.scalingAlgorithm)
        assertEquals(45, photo.sharpnessPercent)
        assertEquals(PhotoTargetResolution.PX_1600, photo.targetResolution)
        assertEquals(90, photo.jpegQuality)
        assertFalse(photo.autoSrgbFix)

        assertEquals(VideoResolution.HD_720P_ULTRA_SHARP, video.resolution)
        assertEquals(FrameRateMode.FPS_60, video.frameRateMode)
        assertEquals(VideoEncoder.GPU_MEDIACODEC, video.encoder)
        assertEquals(BitrateMode.Manual(4_200), video.bitrateMode)
        assertFalse(video.hdrToSdr)
        assertEquals(AutoSplitMode.EVERY_60S, video.autoSplit)

        val uiState = buildAdvancedTuningUiState(MediaType.VIDEO, TargetPlatform.WA_STORY, photo, video, null)
        assertEquals(MediaTuningMode.VIDEO, uiState.mediaMode)
        assertTrue(uiState.isManualBitrate)
        assertEquals(4_200, uiState.manualBitrateKbps)
        assertEquals(EncoderOption.GPU_MEDIACODEC, uiState.encoder)
        assertEquals("Bicubic · USM 45% · GPU", formatAdvancedSummaryBadge(MediaType.VIDEO, photo, video))

        dispatch(AdvancedTuningEvent.ResetAllToOptimal)
        assertEquals(PhotoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY), photo)
        assertEquals(VideoAdvancedConfig.defaultFor(TargetPlatform.WA_STORY), video)
        assertNull(photo.targetResolution)
    }
}
