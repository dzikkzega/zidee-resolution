package com.zidee.ress.presentation.studio

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.CanvasGeometry
import com.zidee.ress.domain.model.MediaMetadata
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimalMediaSpec
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PhotoEncodingSpec
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.ScalingAlgorithm
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.VideoEncoder
import com.zidee.ress.domain.model.VideoEncodingSpec

/**
 * Responsive Studio Editor matching FigCraft `HP 02`, `HP 03`, `HP 05`, `TAB 01`, and `TAB 02`.
 */
@Composable
fun StudioEditorScreen(
    state: StudioEditorUiState,
    historyItems: List<OptimizedMediaItem>,
    onEvent: (StudioEditorUiEvent) -> Unit,
    onBack: () -> Unit,
    onAddMoreMedia: () -> Unit,
    onReShareHistoryItem: (OptimizedMediaItem) -> Unit,
    onShareCompletedItems: (List<OptimizedMediaItem>, TargetPlatform) -> Unit,
    onDoneToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        when {
            maxWidth >= 900.dp ->
                StudioTabletLandscapeLayout(
                    state = state,
                    historyItems = historyItems,
                    onEvent = onEvent,
                    onBack = onBack,
                    onAddMoreMedia = onAddMoreMedia,
                    onReShareHistoryItem = onReShareHistoryItem,
                    onShareCompletedItems = onShareCompletedItems,
                    onDoneToDashboard = onDoneToDashboard,
                )
            maxWidth >= 600.dp ->
                StudioTabletPortraitLayout(
                    state = state,
                    historyItems = historyItems,
                    onEvent = onEvent,
                    onBack = onBack,
                    onAddMoreMedia = onAddMoreMedia,
                    onReShareHistoryItem = onReShareHistoryItem,
                    onShareCompletedItems = onShareCompletedItems,
                    onDoneToDashboard = onDoneToDashboard,
                )
            else ->
                StudioPhoneLayout(
                    state = state,
                    onEvent = onEvent,
                    onBack = onBack,
                    onAddMoreMedia = onAddMoreMedia,
                    onShareCompletedItems = onShareCompletedItems,
                    onDoneToDashboard = onDoneToDashboard,
                )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 360, heightDp = 800)
@Composable
private fun StudioPhonePreview() {
    ZideeTheme {
        StudioEditorScreen(
            state = sampleStudioState(),
            historyItems = emptyList(),
            onEvent = {},
            onBack = {},
            onAddMoreMedia = {},
            onReShareHistoryItem = {},
            onShareCompletedItems = { _, _ -> },
            onDoneToDashboard = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 768, heightDp = 1024)
@Composable
private fun StudioTabletPortraitPreview() {
    ZideeTheme {
        StudioEditorScreen(
            state = sampleStudioState(),
            historyItems = emptyList(),
            onEvent = {},
            onBack = {},
            onAddMoreMedia = {},
            onReShareHistoryItem = {},
            onShareCompletedItems = { _, _ -> },
            onDoneToDashboard = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 1024, heightDp = 768)
@Composable
private fun StudioTabletLandscapePreview() {
    ZideeTheme {
        StudioEditorScreen(
            state = sampleStudioState(),
            historyItems = emptyList(),
            onEvent = {},
            onBack = {},
            onAddMoreMedia = {},
            onReShareHistoryItem = {},
            onShareCompletedItems = { _, _ -> },
            onDoneToDashboard = {},
        )
    }
}

private fun sampleStudioState(): StudioEditorUiState {
    val uri = MediaUri("content://media/external/video/media/941")
    val meta =
        MediaMetadata(
            uri = uri,
            mediaType = MediaType.VIDEO,
            width = 3840,
            height = 2160,
            sizeBytes = 64_000_000L,
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
            geometry = CanvasGeometry.Scale,
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
    return StudioEditorUiState(
        queue = listOf(StudioQueueEntry(uri = uri, metadata = meta, isLoading = false)),
        activeSpec = spec,
    )
}
