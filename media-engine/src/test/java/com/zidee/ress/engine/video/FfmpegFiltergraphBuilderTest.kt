package com.zidee.ress.engine.video

import com.zidee.ress.domain.model.AutoSplitMode
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.FrameRateMode
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizationRequest
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoAdvancedConfig
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegFiltergraphBuilderTest {
    private val calculate = CalculateOptimalSpecUseCase()
    private val builder = FfmpegFiltergraphBuilder()

    @Test
    fun `blur background filtergraph includes lanczos, gblur, brightness darken, unsharp, fps, and yuv420p`() {
        val spec =
            calculate(
                OptimizationRequest(
                    source = video(1920, 1080, fps = 60.0),
                    platform = TargetPlatform.WA_STORY,
                    aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                ),
            )

        val args =
            builder.buildArguments(
                inputPath = "/cache/in.mp4",
                outputPath = "/cache/out.mp4",
                spec = spec,
            )
        val joined = args.joinToString(" ")

        assertTrue(args.contains("-filter_complex"))
        assertTrue(joined.contains("flags=lanczos"))
        assertTrue(joined.contains("gblur=sigma=32"))
        assertTrue(joined.contains("eq=brightness=-0.06"))
        assertTrue(joined.contains("unsharp=5:5:0.45:3:3:0.0"))
        assertTrue(joined.contains("fps=30"))
        assertTrue(joined.contains("format=yuv420p"))
        assertTrue(joined.contains("-c:v libx264"))
        assertTrue(joined.contains("-maxrate 2400k"))
        assertTrue(joined.contains("-bufsize 4800k"))
        assertTrue(joined.contains("-c:a aac -b:a 128k -ar 44100 -ac 2"))
        assertTrue(joined.contains("-movflags +faststart"))
        assertFalse(args.contains("-segment_format"))
    }

    @Test
    fun `blur background filtergraph scales gblur sigma dynamically from blurRadiusPx`() {
        val strongSpec =
            calculate(
                OptimizationRequest(
                    source = video(1920, 1080),
                    platform = TargetPlatform.IG_STORY,
                    aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                    blurRadiusPx = 50,
                ),
            )
        val softSpec =
            calculate(
                OptimizationRequest(
                    source = video(1920, 1080),
                    platform = TargetPlatform.IG_STORY,
                    aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                    blurRadiusPx = 10,
                ),
            )

        val strongComplex = valueAfter(builder.buildArguments("/in.mp4", "/out.mp4", strongSpec), "-filter_complex")
        val softComplex = valueAfter(builder.buildArguments("/in.mp4", "/out.mp4", softSpec), "-filter_complex")

        assertTrue(strongComplex.contains("gblur=sigma=46"))
        assertTrue(softComplex.contains("gblur=sigma=9"))
    }

    @Test
    fun `crop 9 by 16 builds exact pixel crop before lanczos scaling`() {
        val spec =
            calculate(
                OptimizationRequest(
                    source = video(3840, 2160),
                    platform = TargetPlatform.IG_STORY,
                    aspectMode = CanvasAspectMode.CROP_9_16,
                ),
            )

        val args = builder.buildArguments("/in.mp4", "/out.mp4", spec)
        val filter = valueAfter(args, "-vf")

        assertTrue(filter.contains("crop=1216:2160:1312:0"))
        assertTrue(filter.contains("scale=1080:1920:flags=lanczos"))
        assertTrue(filter.contains("format=yuv420p"))
    }

    @Test
    fun `original mode scales to exact calculated modulo-2 size`() {
        val spec =
            calculate(
                OptimizationRequest(
                    source = video(3840, 2160),
                    platform = TargetPlatform.WA_CHAT,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                ),
            )

        val args = builder.buildArguments("/in.mp4", "/out.mp4", spec)
        val filter = valueAfter(args, "-vf")

        assertTrue(filter.contains("scale=1920:1080:flags=lanczos"))
        assertFalse(args.contains("-filter_complex"))
    }

    @Test
    fun `hdr source enables zscale and hable tonemap and stamps bt709 sdr metadata when hdrToSdr is active`() {
        val spec =
            calculate(
                OptimizationRequest(
                    source = video(1920, 1080, isHdr = true),
                    platform = TargetPlatform.IG_STORY,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                    videoConfig = VideoAdvancedConfig(hdrToSdr = true, autoSplit = AutoSplitMode.OFF),
                ),
            )

        val args = builder.buildArguments("/in.mp4", "/out.mp4", spec)
        val filter = valueAfter(args, "-vf")
        val joined = args.joinToString(" ")

        assertTrue(filter.contains("zscale=t=linear:npl=100"))
        assertTrue(filter.contains("tonemap=tonemap=hable:desat=0"))
        assertTrue(filter.contains("zscale=t=bt709:m=bt709:r=tv"))
        assertTrue(joined.contains("-color_primaries bt709 -color_trc bt709 -colorspace bt709 -color_range tv"))
    }

    @Test
    fun `blur background with hdrToSdr applies tonemapping before split and keeps fractional fps`() {
        val spec =
            calculate(
                OptimizationRequest(
                    source = video(1920, 1080, fps = 29.97, isHdr = true),
                    platform = TargetPlatform.IG_STORY,
                    aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                    videoConfig =
                        VideoAdvancedConfig(
                            frameRateMode = FrameRateMode.ORIGINAL,
                            hdrToSdr = true,
                            autoSplit = AutoSplitMode.OFF,
                        ),
                ),
            )

        val args = builder.buildArguments("/in.mp4", "/out.mp4", spec)
        val complex = valueAfter(args, "-filter_complex")

        assertTrue(complex.startsWith("[0:v]zscale=t=linear:npl=100"))
        assertTrue(complex.contains("[hdr_sdr];[hdr_sdr]split=2[bg][fg]"))
        assertTrue(complex.contains("fps=29.97"))
    }

    @Test
    fun `switching encoder to GPU_MEDIACODEC swaps libx264 for h264_mediacodec`() {
        val spec =
            calculate(
                OptimizationRequest(
                    source = video(1920, 1080),
                    platform = TargetPlatform.IG_STORY,
                    aspectMode = CanvasAspectMode.BLUR_BG_9_16,
                    videoConfig = VideoAdvancedConfig(encoder = VideoEncoder.GPU_MEDIACODEC, autoSplit = AutoSplitMode.OFF),
                ),
            )

        val args = builder.buildArguments("/in.mp4", "/out.mp4", spec)
        val joined = args.joinToString(" ")

        assertTrue(joined.contains("-c:v h264_mediacodec"))
        assertFalse(joined.contains("libx264"))
        assertTrue(joined.contains("-b:v 4000k"))
        assertTrue(joined.contains("-maxrate 4500k"))
        assertTrue(joined.contains("gblur=sigma=32"))
    }

    @Test
    fun `auto split configures mp4 segment muxer options with faststart and forced keyframes`() {
        val spec =
            calculate(
                OptimizationRequest(
                    source = video(1080, 1920, durationMs = 75_000),
                    platform = TargetPlatform.WA_STORY,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                ),
            )

        val args = builder.buildArguments("/in.mp4", "/out_%02d.mp4", spec)
        val joined = args.joinToString(" ")

        assertTrue(
            joined.contains(
                "-f segment -segment_format mp4 -segment_format_options movflags=+faststart " +
                    "-segment_time 30 -reset_timestamps 1 -segment_time_delta 0.05",
            ),
        )
        assertTrue(joined.contains("-force_key_frames expr:gte(t,n_forced*30)"))
        assertFalse("Segmented muxer must use -segment_format_options instead of -movflags", args.contains("-movflags"))
        assertEquals("/out_%02d.mp4", args.last())
    }

    @Test
    fun `disabling antiBlurSharpening omits unsharp filter`() {
        val spec =
            calculate(
                OptimizationRequest(
                    source = video(1920, 1080),
                    platform = TargetPlatform.WA_CHAT,
                    aspectMode = CanvasAspectMode.ORIGINAL,
                    videoConfig = VideoAdvancedConfig(antiBlurSharpening = false),
                ),
            )

        val args = builder.buildArguments("/in.mp4", "/out.mp4", spec)
        assertFalse(valueAfter(args, "-vf").contains("unsharp"))
    }

    private fun valueAfter(
        args: List<String>,
        flag: String,
    ): String {
        val idx = args.indexOf(flag)
        assertTrue("Missing flag $flag", idx >= 0 && idx + 1 < args.size)
        return args[idx + 1]
    }

    private fun video(
        width: Int,
        height: Int,
        durationMs: Long = 10_000,
        fps: Double = 30.0,
        isHdr: Boolean = false,
    ) = MediaMetadata(
        uri = MediaUri("content://test/video"),
        mediaType = MediaType.VIDEO,
        width = width,
        height = height,
        sizeBytes = 10_000_000,
        durationMs = durationMs,
        frameRate = fps,
        isHdr = isHdr,
    )
}
