package com.zidee.ress.domain.model

/**
 * How [CanvasAspectMode.ORIGINAL] fits the source into a platform's pixel budget.
 */
sealed interface FitRule {
    /** Box follows the source orientation (landscape source gets a landscape box). */
    data class OrientedBox(val longEdge: Int, val shortEdge: Int) : FitRule

    /** Fixed portrait box, e.g. the 1080x1920 story frame. */
    data class PortraitBox(val width: Int, val height: Int) : FitRule

    /** Width is forced to exactly [width], even when that means upscaling. */
    data class LockWidth(val width: Int) : FitRule
}

/**
 * One row of the PRD "Sweet-Spot Matrix". Values here are the single source of truth
 * for presets; UI and engine read them through [TargetPlatform.preset].
 */
data class PlatformPreset(
    val photoOriginalFit: FitRule,
    val photoSharpnessPercent: Int,
    val photoJpegQuality: Int,
    val videoOriginalFit: FitRule,
    val videoMaxBitrateKbps: Int,
    val defaultAutoSplit: AutoSplitMode,
    /** IG Story re-encodes HDR badly, so SDR conversion is forced regardless of the toggle. */
    val forceSdr: Boolean,
    /** Chat platforms honour [PhotoAdvancedConfig.targetResolution]. */
    val acceptsPhotoTargetResolution: Boolean,
)

private val STORY_BOX = FitRule.PortraitBox(width = 1080, height = 1920)
private val VIDEO_1080P_BOX = FitRule.OrientedBox(longEdge = 1920, shortEdge = 1080)

val TargetPlatform.preset: PlatformPreset
    get() =
        when (this) {
            TargetPlatform.WA_STORY ->
                PlatformPreset(
                    photoOriginalFit = STORY_BOX,
                    photoSharpnessPercent = 28,
                    photoJpegQuality = 94,
                    videoOriginalFit = STORY_BOX,
                    videoMaxBitrateKbps = 2_400,
                    defaultAutoSplit = AutoSplitMode.EVERY_30S,
                    forceSdr = false,
                    acceptsPhotoTargetResolution = false,
                )
            TargetPlatform.WA_CHAT ->
                PlatformPreset(
                    photoOriginalFit = FitRule.OrientedBox(longEdge = 2048, shortEdge = 2048),
                    photoSharpnessPercent = 20,
                    photoJpegQuality = 94,
                    videoOriginalFit = VIDEO_1080P_BOX,
                    videoMaxBitrateKbps = 3_500,
                    defaultAutoSplit = AutoSplitMode.OFF,
                    forceSdr = false,
                    acceptsPhotoTargetResolution = true,
                )
            TargetPlatform.IG_STORY ->
                PlatformPreset(
                    photoOriginalFit = STORY_BOX,
                    photoSharpnessPercent = 25,
                    photoJpegQuality = 95,
                    videoOriginalFit = STORY_BOX,
                    videoMaxBitrateKbps = 4_000,
                    defaultAutoSplit = AutoSplitMode.EVERY_60S,
                    forceSdr = true,
                    acceptsPhotoTargetResolution = false,
                )
            TargetPlatform.IG_POST ->
                PlatformPreset(
                    photoOriginalFit = FitRule.LockWidth(width = 1080),
                    photoSharpnessPercent = 25,
                    photoJpegQuality = 95,
                    videoOriginalFit = FitRule.LockWidth(width = 1080),
                    videoMaxBitrateKbps = 5_000,
                    defaultAutoSplit = AutoSplitMode.OFF,
                    forceSdr = false,
                    acceptsPhotoTargetResolution = false,
                )
            TargetPlatform.IG_CHAT ->
                PlatformPreset(
                    photoOriginalFit = FitRule.OrientedBox(longEdge = 1920, shortEdge = 1920),
                    photoSharpnessPercent = 22,
                    photoJpegQuality = 93,
                    videoOriginalFit = VIDEO_1080P_BOX,
                    videoMaxBitrateKbps = 2_800,
                    defaultAutoSplit = AutoSplitMode.OFF,
                    forceSdr = false,
                    acceptsPhotoTargetResolution = true,
                )
        }
