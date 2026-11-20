package com.zidee.ress.domain.usecase

import com.zidee.ress.domain.model.AutoSplitMode
import com.zidee.ress.domain.model.BitrateMode
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.CropAdjustment
import com.zidee.ress.domain.model.FrameRateMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.PhotoAdvancedConfig
import com.zidee.ress.domain.model.PhotoTargetResolution
import com.zidee.ress.domain.model.PixelRect
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoAdvancedConfig
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateOptimalSpecUseCaseTest {
    private val calculate = CalculateOptimalSpecUseCase()

    // region modulo-2 rounding

    @Test
    fun `roundEven rounds to nearest even with a floor of 2`() {
        assertEquals(1620, EvenDimensions.roundEven(1619.6))
        assertEquals(1366, EvenDimensions.roundEven(1365.33))
        assertEquals(608, EvenDimensions.roundEven(607.5))
        assertEquals(4, EvenDimensions.roundEven(3.0))
        assertEquals(2, EvenDimensions.roundEven(1.0))
        assertEquals(2, EvenDimensions.roundEven(0.2))
    }

    @Test
    fun `floorEven drops odd pixel with a floor of 2`() {
        assertEquals(1000, EvenDimensions.floorEven(1001))
        assertEquals(1000, EvenDimensions.floorEven(1000))
        assertEquals(2, EvenDimensions.floorEven(1))
    }

    @Test
    fun `odd sized photo in WA_STORY original becomes even 1080x1620`() {
        val spec = calculate(request(photo(1333, 1999), TargetPlatform.WA_STORY, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1080, 1620), spec.outputSize)
        assertEquals(CanvasGeometry.Scale, spec.geometry)
    }

    // endregion

    // region ORIGINAL mode per platform

    @Test
    fun `WA_CHAT caps long edge of large landscape photo at 2048`() {
        val spec = calculate(request(photo(6000, 4000), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(2048, 1366), spec.outputSize)
    }

    @Test
    fun `WA_CHAT caps long edge of portrait photo at 2048`() {
        val spec = calculate(request(photo(4000, 6000), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1366, 2048), spec.outputSize)
    }

    @Test
    fun `IG_CHAT caps long edge of photo at 1920`() {
        val spec = calculate(request(photo(4000, 3000), TargetPlatform.IG_CHAT, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1920, 1440), spec.outputSize)
    }

    @Test
    fun `chat platforms never upscale small photos`() {
        val spec = calculate(request(photo(800, 600), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(800, 600), spec.outputSize)
    }

    @Test
    fun `odd sized small photo is floored to even instead of stretched`() {
        val spec = calculate(request(photo(1001, 701), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1000, 700), spec.outputSize)
    }

    @Test
    fun `IG_POST locks width at 1080 for portrait 3 by 4 photo`() {
        val spec = calculate(request(photo(3000, 4000), TargetPlatform.IG_POST, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1080, 1440), spec.outputSize)
    }

    @Test
    fun `IG_POST locks width at 1080 for 4 by 5 photo`() {
        val spec = calculate(request(photo(4000, 5000), TargetPlatform.IG_POST, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1080, 1350), spec.outputSize)
    }

    @Test
    fun `IG_POST upscales narrow photo to exactly 1080 wide`() {
        val spec = calculate(request(photo(720, 900), TargetPlatform.IG_POST, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1080, 1350), spec.outputSize)
    }

    @Test
    fun `story platforms fit landscape photo inside portrait 1080x1920 frame`() {
        val spec = calculate(request(photo(4000, 3000), TargetPlatform.IG_STORY, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1080, 810), spec.outputSize)
    }

    @Test
    fun `photo target resolution overrides long edge cap on chat platforms`() {
        val config = PhotoAdvancedConfig(targetResolution = PhotoTargetResolution.PX_1080)
        val spec =
            calculate(
                request(photo(6000, 4000), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL, photoConfig = config),
            )

        assertEquals(PixelSize(1080, 720), spec.outputSize)
    }

    @Test
    fun `photo target resolution is ignored on story platforms`() {
        val config = PhotoAdvancedConfig(targetResolution = PhotoTargetResolution.PX_2048)
        val spec =
            calculate(
                request(photo(1333, 1999), TargetPlatform.WA_STORY, CanvasAspectMode.ORIGINAL, photoConfig = config),
            )

        assertEquals(PixelSize(1080, 1620), spec.outputSize)
    }

    @Test
    fun `rotation metadata is applied before fitting`() {
        val spec =
            calculate(
                request(video(1920, 1080, rotation = 90), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL),
            )

        assertEquals(PixelSize(1080, 1920), spec.sourceSize)
        assertEquals(PixelSize(1080, 1920), spec.outputSize)
    }

    @Test
    fun `WA_CHAT video downsizes 4K landscape to 1080p`() {
        val spec = calculate(request(video(3840, 2160), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL))

        assertEquals(PixelSize(1920, 1080), spec.outputSize)
        assertEquals(3_500, spec.video?.maxBitrateKbps)
    }

    @Test
    fun `720p ultra sharp scales original budgets by two thirds while keeping 9 by 16 canvas modes at exact 1080x1920`() {
        val config = VideoAdvancedConfig(resolution = VideoResolution.HD_720P_ULTRA_SHARP)

        val chatOriginal =
            calculate(
                request(video(3840, 2160), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL, videoConfig = config),
            )
        val storyOriginal =
            calculate(
                request(video(1080, 1920), TargetPlatform.WA_STORY, CanvasAspectMode.ORIGINAL, videoConfig = config),
            )
        val postOriginal =
            calculate(
                request(video(1080, 1350), TargetPlatform.IG_POST, CanvasAspectMode.ORIGINAL, videoConfig = config),
            )
        val blur =
            calculate(
                request(video(1920, 1080), TargetPlatform.WA_STORY, CanvasAspectMode.BLUR_BG_9_16, videoConfig = config),
            )
        val crop =
            calculate(
                request(video(1920, 1080), TargetPlatform.WA_STORY, CanvasAspectMode.CROP_9_16, videoConfig = config),
            )

        assertEquals(PixelSize(1280, 720), chatOriginal.outputSize)
        assertEquals(PixelSize(720, 1280), storyOriginal.outputSize)
        assertEquals(PixelSize(720, 900), postOriginal.outputSize)
        assertEquals(PixelSize(1080, 1920), blur.outputSize)
        assertEquals(CanvasGeometry.BlurBackground(PixelRect(0, 656, 1080, 608)), blur.geometry)
        assertEquals(PixelSize(1080, 1920), crop.outputSize)
        assertEquals(CanvasGeometry.Crop(PixelRect(656, 0, 608, 1080)), crop.geometry)
    }

    // endregion

    // region 9:16 canvas modes

    @Test
    fun `blur and crop 9 by 16 always emit exact 1080x1920 across every platform and video resolution preset`() {
        val sources = listOf(video(3840, 2160), video(1920, 1080), video(1080, 1920), video(1080, 1080), video(1333, 1999))
        for (resolution in VideoResolution.entries) {
            val videoConfig = VideoAdvancedConfig(resolution = resolution)
            for (platform in TargetPlatform.entries) {
                for (source in sources) {
                    val blur = calculate(request(source, platform, CanvasAspectMode.BLUR_BG_9_16, videoConfig = videoConfig))
                    val crop = calculate(request(source, platform, CanvasAspectMode.CROP_9_16, videoConfig = videoConfig))
                    val tag = "$resolution/$platform/${source.width}x${source.height}"

                    assertEquals("BLUR_BG_9_16 must be exact 1080x1920 ($tag)", PixelSize(1080, 1920), blur.outputSize)
                    assertEquals("CROP_9_16 must be exact 1080x1920 ($tag)", PixelSize(1080, 1920), crop.outputSize)
                    assertTrue("Expected BlurBackground geometry ($tag)", blur.geometry is CanvasGeometry.BlurBackground)
                    assertTrue("Expected Crop geometry ($tag)", crop.geometry is CanvasGeometry.Crop)
                    assertGeometryFits(blur, "blur/$tag")
                    assertGeometryFits(crop, "crop/$tag")
                }
            }
        }
    }

    @Test
    fun `WA_STORY blur background video is absolute 1080x1920 at 2400 kbps and 30 fps`() {
        val spec = calculate(request(video(1920, 1080, fps = 60.0), TargetPlatform.WA_STORY, CanvasAspectMode.BLUR_BG_9_16))

        assertEquals(PixelSize(1080, 1920), spec.outputSize)
        assertEquals(CanvasGeometry.BlurBackground(PixelRect(0, 656, 1080, 608)), spec.geometry)
        val video = requireNotNull(spec.video)
        assertEquals(2_400, video.maxBitrateKbps)
        assertEquals(4_800, video.bufferSizeKbps)
        assertEquals(30.0, video.frameRate, 0.0)
    }

    @Test
    fun `blur background propagates custom blurRadiusPx into geometry`() {
        val soft =
            calculate(
                request(photo(4000, 3000), TargetPlatform.IG_STORY, CanvasAspectMode.BLUR_BG_9_16, blurRadiusPx = 20),
            )
        val strong =
            calculate(
                request(photo(4000, 3000), TargetPlatform.IG_STORY, CanvasAspectMode.BLUR_BG_9_16, blurRadiusPx = 55),
            )

        assertEquals(20, (soft.geometry as CanvasGeometry.BlurBackground).blurRadiusPx)
        assertEquals(55, (strong.geometry as CanvasGeometry.BlurBackground).blurRadiusPx)
    }

    @Test
    fun `blur background upscales small foreground to fill canvas width`() {
        val spec = calculate(request(photo(540, 540), TargetPlatform.WA_CHAT, CanvasAspectMode.BLUR_BG_9_16))

        assertEquals(CanvasGeometry.BlurBackground(PixelRect(0, 420, 1080, 1080)), spec.geometry)
    }

    @Test
    fun `centre crop on landscape photo takes full height 9 by 16 window`() {
        val spec = calculate(request(photo(4000, 3000), TargetPlatform.IG_STORY, CanvasAspectMode.CROP_9_16))

        assertEquals(PixelSize(1080, 1920), spec.outputSize)
        assertEquals(CanvasGeometry.Crop(PixelRect(1156, 0, 1688, 3000)), spec.geometry)
    }

    @Test
    fun `crop pan moves window to the edges`() {
        val left =
            calculate(
                request(photo(4000, 3000), TargetPlatform.IG_STORY, CanvasAspectMode.CROP_9_16, crop = CropAdjustment(panX = 0.0)),
            )
        val right =
            calculate(
                request(photo(4000, 3000), TargetPlatform.IG_STORY, CanvasAspectMode.CROP_9_16, crop = CropAdjustment(panX = 1.0)),
            )

        assertEquals(CanvasGeometry.Crop(PixelRect(0, 0, 1688, 3000)), left.geometry)
        assertEquals(CanvasGeometry.Crop(PixelRect(2312, 0, 1688, 3000)), right.geometry)
    }

    @Test
    fun `crop zoom shrinks window around the pan point`() {
        val spec =
            calculate(
                request(photo(4000, 3000), TargetPlatform.IG_STORY, CanvasAspectMode.CROP_9_16, crop = CropAdjustment(zoom = 2.0)),
            )

        assertEquals(CanvasGeometry.Crop(PixelRect(1578, 750, 844, 1500)), spec.geometry)
    }

    @Test
    fun `crop on very tall source takes full width window`() {
        val spec = calculate(request(photo(1000, 3000), TargetPlatform.WA_STORY, CanvasAspectMode.CROP_9_16))

        assertEquals(CanvasGeometry.Crop(PixelRect(0, 612, 1000, 1778)), spec.geometry)
    }

    // endregion

    // region matrix sweep

    @Test
    fun `every platform mode and common aspect ratio yields even sizes within budget`() {
        // Common ratios: 16:9, 4:3, 9:16, 1:1, 20:9, and odd prime-like dimensions
        val sources =
            listOf(
                3840 to 2160,
                4000 to 3000,
                1080 to 1920,
                3000 to 3000,
                2400 to 1080,
                1333 to 1999,
            )
        val mediaMatrix = sources.flatMap { (w, h) -> listOf(photo(w, h), video(w, h)) }
        for (platform in TargetPlatform.entries) {
            for (mode in CanvasAspectMode.entries) {
                verifyMatrixCases(platform, mode, mediaMatrix)
            }
        }
    }

    private fun verifyMatrixCases(
        platform: TargetPlatform,
        mode: CanvasAspectMode,
        mediaMatrix: List<MediaMetadata>,
    ) {
        for (media in mediaMatrix) {
            val spec = calculate(request(media, platform, mode))
            val label = "$platform/$mode/${media.mediaType}/${media.width}x${media.height} -> ${spec.outputSize}"

            assertTrue("odd width $label", spec.outputSize.width % 2 == 0)
            assertTrue("odd height $label", spec.outputSize.height % 2 == 0)
            assertGeometryFits(spec, label)
            if (mode != CanvasAspectMode.ORIGINAL) {
                assertEquals(label, PixelSize(1080, 1920), spec.outputSize)
            } else {
                assertTrue("over budget $label", spec.outputSize.width <= 2048 && spec.outputSize.height <= 2048)
            }
        }
    }

    private fun assertGeometryFits(
        spec: OptimalMediaSpec,
        label: String,
    ) {
        when (val geometry = spec.geometry) {
            CanvasGeometry.Scale -> Unit
            is CanvasGeometry.Crop -> {
                val r = geometry.sourceRegion
                assertTrue("crop outside source $label", r.left + r.width <= spec.sourceSize.width)
                assertTrue("crop outside source $label", r.top + r.height <= spec.sourceSize.height)
                assertTrue("odd crop $label", r.width % 2 == 0 && r.height % 2 == 0)
            }
            is CanvasGeometry.BlurBackground -> {
                val r = geometry.foreground
                assertTrue("fg outside canvas $label", r.left + r.width <= spec.outputSize.width)
                assertTrue("fg outside canvas $label", r.top + r.height <= spec.outputSize.height)
                assertTrue("odd fg $label", r.width % 2 == 0 && r.height % 2 == 0)
            }
        }
    }

    // endregion

    // region video encoding

    @Test
    fun `lock 30 never raises frame rate above source`() {
        assertEquals(30.0, fpsFor(FrameRateMode.LOCK_30, source = 60.0), 0.0)
        assertEquals(24.0, fpsFor(FrameRateMode.LOCK_30, source = 24.0), 0.0)
        assertEquals(30.0, fpsFor(FrameRateMode.LOCK_30, source = null), 0.0)
    }

    @Test
    fun `60 fps mode is capped at source and original keeps source`() {
        assertEquals(60.0, fpsFor(FrameRateMode.FPS_60, source = 60.0), 0.0)
        assertEquals(29.97, fpsFor(FrameRateMode.FPS_60, source = 29.97), 0.0)
        assertEquals(25.0, fpsFor(FrameRateMode.ORIGINAL, source = 25.0), 0.0)
        assertEquals(30.0, fpsFor(FrameRateMode.ORIGINAL, source = null), 0.0)
    }

    @Test
    fun `platform bitrate ceilings follow the sweet spot matrix`() {
        val expected =
            mapOf(
                TargetPlatform.WA_STORY to 2_400,
                TargetPlatform.WA_CHAT to 3_500,
                TargetPlatform.IG_STORY to 4_000,
                TargetPlatform.IG_POST to 5_000,
                TargetPlatform.IG_CHAT to 2_800,
            )
        for ((platform, kbps) in expected) {
            val video = requireNotNull(calculate(request(video(1920, 1080), platform, CanvasAspectMode.ORIGINAL)).video)
            assertEquals("$platform", kbps, video.maxBitrateKbps)
            assertEquals("$platform", kbps * 2, video.bufferSizeKbps)
            assertEquals(128, video.audioBitrateKbps)
            assertEquals(44_100, video.audioSampleRateHz)
        }
    }

    @Test
    fun `manual bitrate overrides the preset and encoder choice passes through`() {
        val config = VideoAdvancedConfig(bitrateMode = BitrateMode.Manual(8_000), encoder = VideoEncoder.GPU_MEDIACODEC)
        val video =
            requireNotNull(
                calculate(request(video(1920, 1080), TargetPlatform.WA_STORY, CanvasAspectMode.CROP_9_16, videoConfig = config)).video,
            )

        assertEquals(8_000, video.maxBitrateKbps)
        assertEquals(16_000, video.bufferSizeKbps)
        assertEquals(VideoEncoder.GPU_MEDIACODEC, video.encoder)
    }

    @Test
    fun `HDR to SDR is forced on IG_STORY and follows the toggle elsewhere`() {
        val off = VideoAdvancedConfig(hdrToSdr = false)
        val hdr = video(1920, 1080, hdr = true)

        assertTrue(hdrFor(hdr, TargetPlatform.IG_STORY, off))
        assertEquals(false, hdrFor(hdr, TargetPlatform.WA_CHAT, off))
        assertTrue(hdrFor(hdr, TargetPlatform.WA_CHAT, VideoAdvancedConfig(hdrToSdr = true)))
        assertEquals(false, hdrFor(video(1920, 1080, hdr = false), TargetPlatform.IG_STORY, off))
    }

    // endregion

    // region auto split

    @Test
    fun `75 second WA_STORY video splits into 30 30 15`() {
        val video = splitFor(TargetPlatform.WA_STORY, durationMs = 75_000)

        assertEquals(30, video.segmentSeconds)
        assertEquals(listOf(30_000L, 30_000L, 15_000L), video.segmentDurationsMs)
    }

    @Test
    fun `exact multiple splits evenly without an empty tail`() {
        val video = splitFor(TargetPlatform.WA_STORY, durationMs = 60_000)

        assertEquals(listOf(30_000L, 30_000L), video.segmentDurationsMs)
    }

    @Test
    fun `IG_STORY splits every 60 seconds by default`() {
        val video = splitFor(TargetPlatform.IG_STORY, durationMs = 150_000)

        assertEquals(60, video.segmentSeconds)
        assertEquals(listOf(60_000L, 60_000L, 30_000L), video.segmentDurationsMs)
    }

    @Test
    fun `video shorter than or equal to the segment is not split`() {
        val video = splitFor(TargetPlatform.WA_STORY, durationMs = 30_000)

        assertNull(video.segmentSeconds)
        assertEquals(listOf(30_000L), video.segmentDurationsMs)
    }

    @Test
    fun `non story platforms and auto split OFF do not split`() {
        val chat = splitFor(TargetPlatform.WA_CHAT, durationMs = 75_000)
        val off = splitFor(TargetPlatform.WA_STORY, durationMs = 75_000, autoSplit = AutoSplitMode.OFF)

        assertNull(chat.segmentSeconds)
        assertEquals(listOf(75_000L), chat.segmentDurationsMs)
        assertNull(off.segmentSeconds)
    }

    @Test
    fun `user can pick 60 second split on WA_STORY`() {
        val video = splitFor(TargetPlatform.WA_STORY, durationMs = 75_000, autoSplit = AutoSplitMode.EVERY_60S)

        assertEquals(listOf(60_000L, 15_000L), video.segmentDurationsMs)
    }

    @Test
    fun `unknown duration keeps segment length and plans no parts`() {
        val video = splitFor(TargetPlatform.WA_STORY, durationMs = null)

        assertEquals(30, video.segmentSeconds)
        assertTrue(video.segmentDurationsMs.isEmpty())
    }

    @Test
    fun `corrupted huge durationMs caps planned segments instead of overflowing memory`() {
        val video = splitFor(TargetPlatform.WA_STORY, durationMs = Long.MAX_VALUE / 2)

        assertEquals(30, video.segmentSeconds)
        assertTrue(video.segmentDurationsMs.size <= 1_001)
    }

    // endregion

    // region photo encoding & estimates

    @Test
    fun `photo presets follow the sweet spot matrix`() {
        val expected =
            mapOf(
                TargetPlatform.WA_STORY to (28 to 94),
                TargetPlatform.WA_CHAT to (20 to 94),
                TargetPlatform.IG_STORY to (25 to 95),
                TargetPlatform.IG_POST to (25 to 95),
                TargetPlatform.IG_CHAT to (22 to 93),
            )
        for ((platform, values) in expected) {
            val spec = calculate(request(photo(4000, 3000), platform, CanvasAspectMode.ORIGINAL))
            val photo = requireNotNull(spec.photo)
            assertEquals("$platform", values.first, photo.sharpnessPercent)
            assertEquals("$platform", values.second, photo.jpegQuality)
            assertNull(spec.video)
        }
    }

    @Test
    fun `sharpness percent maps linearly to unsharp alpha 0 to 1_5`() {
        assertEquals(0.375, alphaFor(25), 1e-9)
        assertEquals(0.0, alphaFor(0), 1e-9)
        assertEquals(1.5, alphaFor(100), 1e-9)
    }

    @Test
    fun `sRGB conversion follows toggle but is forced on IG_STORY`() {
        val off = PhotoAdvancedConfig(autoSrgbFix = false)

        val igStory = calculate(request(photo(4000, 3000), TargetPlatform.IG_STORY, CanvasAspectMode.ORIGINAL, photoConfig = off))
        val waChat = calculate(request(photo(4000, 3000), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL, photoConfig = off))

        assertTrue(requireNotNull(igStory.photo).convertToSrgb)
        assertEquals(false, requireNotNull(waChat.photo).convertToSrgb)
    }

    @Test
    fun `video size estimate uses bitrate ceiling plus audio over duration`() {
        val spec = calculate(request(video(1920, 1080, durationMs = 10_000), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL))

        assertEquals(4_535_000L, spec.estimatedSizeBytes)
        assertNull(spec.photo)
    }

    @Test
    fun `photo size estimate is positive and grows with quality`() {
        val low =
            calculate(
                request(
                    photo(4000, 3000),
                    TargetPlatform.WA_CHAT,
                    CanvasAspectMode.ORIGINAL,
                    photoConfig = PhotoAdvancedConfig(jpegQuality = 80),
                ),
            )
        val high =
            calculate(
                request(
                    photo(4000, 3000),
                    TargetPlatform.WA_CHAT,
                    CanvasAspectMode.ORIGINAL,
                    photoConfig = PhotoAdvancedConfig(jpegQuality = 100),
                ),
            )

        val lowBytes = requireNotNull(low.estimatedSizeBytes)
        val highBytes = requireNotNull(high.estimatedSizeBytes)
        assertTrue(lowBytes > 0)
        assertTrue(highBytes > lowBytes)
    }

    @Test
    fun `video with unknown duration has no size estimate`() {
        val spec = calculate(request(video(1920, 1080, durationMs = null), TargetPlatform.WA_CHAT, CanvasAspectMode.ORIGINAL))

        assertNull(spec.estimatedSizeBytes)
        assertNotNull(spec.video)
    }

    // endregion

    // region helpers

    private fun fpsFor(
        mode: FrameRateMode,
        source: Double?,
    ): Double =
        requireNotNull(
            calculate(
                request(
                    video(1920, 1080, fps = source),
                    TargetPlatform.WA_CHAT,
                    CanvasAspectMode.ORIGINAL,
                    videoConfig = VideoAdvancedConfig(frameRateMode = mode),
                ),
            ).video,
        ).frameRate

    private fun hdrFor(
        media: MediaMetadata,
        platform: TargetPlatform,
        config: VideoAdvancedConfig,
    ): Boolean = requireNotNull(calculate(request(media, platform, CanvasAspectMode.ORIGINAL, videoConfig = config)).video).applyHdrToSdr

    private fun splitFor(
        platform: TargetPlatform,
        durationMs: Long?,
        autoSplit: AutoSplitMode? = null,
    ) = requireNotNull(
        calculate(
            request(
                video(1080, 1920, durationMs = durationMs),
                platform,
                CanvasAspectMode.ORIGINAL,
                videoConfig = autoSplit?.let { VideoAdvancedConfig(autoSplit = it) } ?: VideoAdvancedConfig.defaultFor(platform),
            ),
        ).video,
    )

    private fun alphaFor(percent: Int): Double =
        requireNotNull(
            calculate(
                request(
                    photo(4000, 3000),
                    TargetPlatform.WA_CHAT,
                    CanvasAspectMode.ORIGINAL,
                    photoConfig = PhotoAdvancedConfig(sharpnessPercent = percent),
                ),
            ).photo,
        ).unsharpAmount

    private fun request(
        media: MediaMetadata,
        platform: TargetPlatform,
        mode: CanvasAspectMode,
        photoConfig: PhotoAdvancedConfig = PhotoAdvancedConfig.defaultFor(platform),
        videoConfig: VideoAdvancedConfig = VideoAdvancedConfig.defaultFor(platform),
        crop: CropAdjustment = CropAdjustment.CENTER,
        blurRadiusPx: Int = CanvasGeometry.DEFAULT_BLUR_RADIUS_PX,
    ) = OptimizationRequest(media, platform, mode, photoConfig, videoConfig, crop, blurRadiusPx)

    private fun photo(
        width: Int,
        height: Int,
        rotation: Int = 0,
    ) = MediaMetadata(
        uri = MediaUri("content://test/photo"),
        mediaType = MediaType.PHOTO,
        width = width,
        height = height,
        sizeBytes = 4_000_000,
        rotationDegrees = rotation,
    )

    private fun video(
        width: Int,
        height: Int,
        durationMs: Long? = 10_000,
        fps: Double? = 30.0,
        hdr: Boolean = false,
        rotation: Int = 0,
    ) = MediaMetadata(
        uri = MediaUri("content://test/video"),
        mediaType = MediaType.VIDEO,
        width = width,
        height = height,
        sizeBytes = 40_000_000,
        rotationDegrees = rotation,
        durationMs = durationMs,
        frameRate = fps,
        isHdr = hdr,
    )

    // endregion
}
