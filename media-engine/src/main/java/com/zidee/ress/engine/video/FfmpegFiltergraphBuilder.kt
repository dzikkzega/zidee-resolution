package com.zidee.ress.engine.video

import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.model.VideoEncodingSpec
import kotlin.math.roundToInt

/**
 * Builds deterministic FFmpeg argument arrays (`List<String>`) from [OptimalMediaSpec].
 * Never interpolates external metadata or shell syntax into command strings.
 */
class FfmpegFiltergraphBuilder {
    fun buildArguments(
        inputPath: String,
        outputPath: String,
        spec: OptimalMediaSpec,
    ): List<String> {
        val video = requireNotNull(spec.video) { "FfmpegFiltergraphBuilder requires video spec" }
        val args = mutableListOf("-y", "-i", inputPath)

        appendFilterArgs(args, spec, video)
        appendVideoEncoderArgs(args, video)
        appendAudioAndMuxerArgs(args, video)
        args.add(outputPath)
        return args
    }

    private fun appendFilterArgs(
        args: MutableList<String>,
        spec: OptimalMediaSpec,
        video: VideoEncodingSpec,
    ) {
        val outW = spec.outputSize.width
        val outH = spec.outputSize.height
        val fpsExpr = formatFps(video.frameRate)
        val unsharpClause = if (video.sharpen) ",$UNSHARP_FILTER" else ""
        val hdrChain = if (video.applyHdrToSdr) HDR_TO_SDR_CHAIN else null

        when (val geometry = spec.geometry) {
            CanvasGeometry.Scale -> {
                val chain =
                    buildString {
                        if (hdrChain != null) append(hdrChain).append(',')
                        append("scale=$outW:$outH:flags=lanczos,")
                        append("fps=$fpsExpr")
                        append(unsharpClause)
                        append(",format=yuv420p")
                    }
                args += listOf("-map", "0:v:0", "-map", "0:a?", "-vf", chain)
            }
            is CanvasGeometry.Crop -> {
                val r = geometry.sourceRegion
                val chain =
                    buildString {
                        if (hdrChain != null) append(hdrChain).append(',')
                        append("crop=${r.width}:${r.height}:${r.left}:${r.top},")
                        append("scale=$outW:$outH:flags=lanczos,")
                        append("fps=$fpsExpr")
                        append(unsharpClause)
                        append(",format=yuv420p")
                    }
                args += listOf("-map", "0:v:0", "-map", "0:a?", "-vf", chain)
            }
            is CanvasGeometry.BlurBackground -> {
                val blurSigma = mapRadiusToSigma(geometry.blurRadiusPx)
                val complex =
                    buildString {
                        val splitSource =
                            if (hdrChain != null) {
                                append("[0:v]").append(hdrChain).append("[hdr_sdr];")
                                "[hdr_sdr]"
                            } else {
                                "[0:v]"
                            }
                        append("${splitSource}split=2[bg][fg];")
                        append(
                            "[bg]scale=$outW:$outH:force_original_aspect_ratio=increase:flags=bilinear," +
                                "crop=$outW:$outH,gblur=sigma=$blurSigma,eq=brightness=-0.06[bg_blurred];",
                        )
                        append(
                            "[fg]scale=$outW:$outH:force_original_aspect_ratio=decrease:flags=lanczos" +
                                "$unsharpClause[fg_sharp];",
                        )
                        append("[bg_blurred][fg_sharp]overlay=(W-w)/2:(H-h)/2,fps=$fpsExpr,format=yuv420p[outv]")
                    }
                args += listOf("-filter_complex", complex, "-map", "[outv]", "-map", "0:a?")
            }
        }
    }

    private fun mapRadiusToSigma(radiusPx: Int): Int {
        val clamped = radiusPx.coerceIn(CanvasGeometry.BLUR_RADIUS_RANGE)
        return (clamped * DEFAULT_GBLUR_SIGMA / CanvasGeometry.DEFAULT_BLUR_RADIUS_PX.toDouble())
            .roundToInt()
            .coerceIn(MIN_GBLUR_SIGMA, MAX_GBLUR_SIGMA)
    }

    private fun appendVideoEncoderArgs(
        args: MutableList<String>,
        video: VideoEncodingSpec,
    ) {
        val gop = (video.frameRate * 2.0).roundToInt().coerceAtLeast(30)
        val minKey = video.frameRate.roundToInt().coerceAtLeast(15)

        when (video.encoder) {
            VideoEncoder.CPU_LIBX264 -> {
                args +=
                    listOf(
                        "-c:v", "libx264",
                        "-profile:v", "high",
                        "-level", "4.1",
                        "-preset", "medium",
                        "-crf", "21",
                        "-maxrate", "${video.maxBitrateKbps}k",
                        "-bufsize", "${video.bufferSizeKbps}k",
                        "-g", "$gop",
                        "-keyint_min", "$minKey",
                        "-sc_threshold", "40",
                    )
            }
            VideoEncoder.GPU_MEDIACODEC -> {
                val gpuMaxKbps =
                    if (video.maxBitrateKbps < 3_000) {
                        video.maxBitrateKbps + 400
                    } else {
                        video.maxBitrateKbps + 500
                    }
                args +=
                    listOf(
                        "-c:v", "h264_mediacodec",
                        "-profile:v", "high",
                        "-b:v", "${video.maxBitrateKbps}k",
                        "-maxrate", "${gpuMaxKbps}k",
                        "-g", "$gop",
                    )
            }
        }

        if (video.applyHdrToSdr) {
            args +=
                listOf(
                    "-color_primaries", "bt709",
                    "-color_trc", "bt709",
                    "-colorspace", "bt709",
                    "-color_range", "tv",
                )
        }
    }

    private fun appendAudioAndMuxerArgs(
        args: MutableList<String>,
        video: VideoEncodingSpec,
    ) {
        args +=
            listOf(
                "-c:a", "aac",
                "-b:a", "${video.audioBitrateKbps}k",
                "-ar", "${video.audioSampleRateHz}",
                "-ac", "2",
            )
        val segmentSec = video.segmentSeconds
        if (segmentSec != null) {
            args +=
                listOf(
                    "-f", "segment",
                    "-segment_format", "mp4",
                    "-segment_format_options", "movflags=+faststart",
                    "-segment_time", "$segmentSec",
                    "-reset_timestamps", "1",
                    "-segment_time_delta", "0.05",
                    "-force_key_frames", "expr:gte(t,n_forced*$segmentSec)",
                )
        } else {
            args += listOf("-movflags", "+faststart")
        }
    }

    private fun formatFps(fps: Double): String {
        val rounded = fps.roundToInt()
        return if (kotlin.math.abs(fps - rounded) < 1e-4) {
            rounded.toString()
        } else {
            ((fps * 100.0).roundToInt() / 100.0).toString()
        }
    }

    private companion object {
        const val DEFAULT_GBLUR_SIGMA = 32.0
        const val MIN_GBLUR_SIGMA = 8
        const val MAX_GBLUR_SIGMA = 60
        const val UNSHARP_FILTER = "unsharp=5:5:0.45:3:3:0.0"
        const val HDR_TO_SDR_CHAIN =
            "zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709," +
                "tonemap=tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p"
    }
}
