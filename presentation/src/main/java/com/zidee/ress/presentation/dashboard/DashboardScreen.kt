package com.zidee.ress.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zidee.ress.core.format.MediaFormatters
import com.zidee.ress.designsystem.component.PlatformPresetSelector
import com.zidee.ress.designsystem.component.zideeTactileScale
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.CanvasAspectMode
import com.zidee.ress.domain.model.MediaType
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.OptimizedMediaItem
import com.zidee.ress.domain.model.PixelSize
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.domain.model.UserPreferences
import com.zidee.ress.presentation.common.ErrorAlertBanner
import com.zidee.ress.presentation.common.MiniLogoBadge
import com.zidee.ress.presentation.common.OfflineEnginePill
import com.zidee.ress.presentation.common.PermanentFolderBanner
import com.zidee.ress.presentation.common.ZideePresentationDefaults
import com.zidee.ress.presentation.common.rememberMediaThumbnail
import com.zidee.ress.presentation.common.toCompactBadge

private enum class DashboardTab {
    STUDIO_HOME,
    ALBUM_GALLERY,
}

/**
 * Main Dashboard implementing FigCraft `HP 01 · Home Dashboard & ZideeResolution`
 * with responsive grid columns on tablets.
 */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onEvent: (DashboardUiEvent) -> Unit,
    onOpenPrimaryPicker: (TargetPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    var activeTab by rememberSaveable { mutableStateOf(DashboardTab.STUDIO_HOME) }
    var selectedShortcutPlatform by rememberSaveable(state.preferences.lastSelectedPlatform) {
        mutableStateOf(state.preferences.lastSelectedPlatform)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bgCanvas,
        topBar = {
            DashboardTopHeader(
                modifier =
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        },
        bottomBar = {
            DashboardBottomBar(
                activeTab = activeTab,
                itemCount = state.historyItems.size,
                onSelectTab = { activeTab = it },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val gridColumns =
                when {
                    maxWidth >= 900.dp -> 4
                    maxWidth >= 600.dp -> 3
                    else -> 2
                }
            DashboardContentGrid(
                state = state,
                activeTab = activeTab,
                gridColumns = gridColumns,
                selectedShortcutPlatform = selectedShortcutPlatform,
                onSelectShortcutPlatform = { platform ->
                    selectedShortcutPlatform = platform
                    onEvent(DashboardUiEvent.SelectPlatformShortcut(platform))
                },
                onOpenPrimaryPicker = { onOpenPrimaryPicker(selectedShortcutPlatform) },
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun DashboardTopHeader(modifier: Modifier = Modifier) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniLogoBadge(versionLabel = "v1.0")
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Zidee Ress",
                    style = typography.sectionHeader,
                    color = colors.contrastWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Pro Darkroom Media Optimizer",
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
        OfflineEnginePill(text = "● ON-DEVICE")
    }
}

@Composable
private fun DashboardContentGrid(
    state: DashboardUiState,
    activeTab: DashboardTab,
    gridColumns: Int,
    selectedShortcutPlatform: TargetPlatform,
    onSelectShortcutPlatform: (TargetPlatform) -> Unit,
    onOpenPrimaryPicker: () -> Unit,
    onEvent: (DashboardUiEvent) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.errorMessage.isNullOrBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorAlertBanner(
                    message = state.errorMessage,
                    onDismiss = { onEvent(DashboardUiEvent.DismissError) },
                )
            }
        }

        if (activeTab == DashboardTab.STUDIO_HOME) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HeroActionCard(
                    selectedPlatform = selectedShortcutPlatform,
                    onPickMediaClick = onOpenPrimaryPicker,
                    onPlatformShortcutClick = onSelectShortcutPlatform,
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            PermanentFolderBanner(text = "Album Permanen: Galeri/ZideeResolution")
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            AlbumSectionHeader(
                itemCount = state.historyItems.size,
                isAlbumTab = activeTab == DashboardTab.ALBUM_GALLERY,
                onReShareLatestBatch = {
                    val latest = state.historyItems.take(4)
                    if (latest.isNotEmpty()) {
                        onEvent(DashboardUiEvent.ReShareItems(latest))
                    }
                },
            )
        }

        if (state.isLoadingHistory && state.historyItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AlbumLoadingState()
            }
        } else if (state.historyItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AlbumEmptyState(onPickMediaClick = onOpenPrimaryPicker)
            }
        } else {
            itemsIndexed(
                items = state.historyItems,
                key = { _, item -> item.id },
            ) { index, item ->
                AlbumHistoryCard(
                    item = item,
                    highlight = index == 0,
                    onReShare = { onEvent(DashboardUiEvent.ReShareItem(item)) },
                    onDelete = { onEvent(DashboardUiEvent.DeleteHistoryItem(item.id)) },
                )
            }
        }
    }
}

@Composable
private fun HeroActionCard(
    selectedPlatform: TargetPlatform,
    onPickMediaClick: () -> Unit,
    onPlatformShortcutClick: (TargetPlatform) -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.card,
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.accentAmber.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "SWEET-SPOT PRE-PROCESSING ENGINE",
                style = typography.metricMono,
                color = colors.accentAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
            )
            Text(
                text = "Optimasi Foto & Video HD+",
                style = typography.displayTitle,
                color = colors.contrastWhite,
                fontSize = 20.sp,
            )
            Text(
                text = "Lanczos-3 · Unsharp Mask · Kunci 30fps SDR · Anti Pecah",
                style = typography.caption,
                color = colors.textSecondary,
            )
            HeroPrimaryPickButton(onClick = onPickMediaClick)
            Text(
                text = "TARGET PRESET CEPAT:",
                style = typography.metricMono,
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            PlatformPresetSelector(
                options = ZideePresentationDefaults.platformOptions,
                selectedId = selectedPlatform.name,
                onSelect = { rawId ->
                    TargetPlatform.entries.firstOrNull { it.name == rawId }?.let(onPlatformShortcutClick)
                },
            )
        }
    }
}

@Composable
private fun HeroPrimaryPickButton(onClick: () -> Unit) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .zideeTactileScale(interactionSource = interactionSource),
        shape = shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.accentAmber,
                contentColor = colors.onAccent,
            ),
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = "Pilih Foto atau Video (Single / Batch)",
            style = typography.controlLabel,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AlbumSectionHeader(
    itemCount: Int,
    isAlbumTab: Boolean,
    onReShareLatestBatch: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isAlbumTab) "Semua Media di ZideeResolution" else "Tersimpan di ZideeResolution",
            style = typography.sectionHeader,
            color = colors.contrastWhite,
            fontSize = 15.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isAlbumTab && itemCount > 1) {
                Text(
                    text = "Kirim Batch",
                    style = typography.controlLabelSmall,
                    color = colors.accentAmberBright,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onReShareLatestBatch)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Text(
                text = "$itemCount ITEM · SIAP",
                style = typography.metricBadge,
                color = colors.accentAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AlbumHistoryCard(
    item: OptimizedMediaItem,
    highlight: Boolean,
    onReShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val borderColor = if (highlight) colors.accentAmber.copy(alpha = 0.36f) else colors.borderSubtle
    val resLine = MediaFormatters.formatResolution(item.outputSize.width, item.outputSize.height)
    val bytesLine = MediaFormatters.formatBytes(item.outputSizeBytes)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlbumCardThumbnailBox(
                item = item,
                highlight = highlight,
                onDelete = onDelete,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.displayName,
                    style = typography.controlLabelSmall,
                    color = colors.contrastWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$resLine · $bytesLine",
                    style = typography.metricMono,
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onReShare,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = ZideeMinTouchTarget),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, if (highlight) colors.borderActive else colors.borderSubtle),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.bgElevated,
                        contentColor = if (highlight) colors.accentAmberBright else colors.contrastWhite,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(modifier = Modifier.size(5.dp))
                Text(
                    text = "Kirim Ulang ke WA/IG",
                    style = typography.controlLabelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AlbumCardThumbnailBox(
    item: OptimizedMediaItem,
    highlight: Boolean,
    onDelete: () -> Unit,
) {
    val colors = ZideeTheme.colors
    val thumbnail =
        rememberMediaThumbnail(
            uri = item.mediaStoreUri.value,
            mediaType = item.mediaType,
            maxDimensionPx = 280,
        )
    val specSubTag =
        when (item.aspectMode) {
            CanvasAspectMode.BLUR_BG_9_16 -> {
                val dur = item.durationMs?.let { " · ${MediaFormatters.formatDuration(it)}" }.orEmpty()
                "BLUR BG$dur"
            }
            CanvasAspectMode.CROP_9_16 -> "CROP 9:16 · sRGB"
            CanvasAspectMode.ORIGINAL -> "LANCZOS3 · HD+"
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bgElevated),
    ) {
        if (thumbnail != null) {
            AlbumThumbnailBackdrop(thumbnail = thumbnail, contentDescription = item.displayName)
        }
        ThumbnailOverlayTag(
            text = item.platform.toCompactBadge(),
            textColor = if (highlight) colors.accentAmberBright else colors.statusSuccess,
            isBold = true,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
        )
        IconButton(
            onClick = onDelete,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(ZideeMinTouchTarget),
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Hapus ${item.displayName} dari riwayat",
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        ThumbnailOverlayTag(
            text = specSubTag,
            textColor = colors.contrastWhite,
            isBold = false,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
        )
    }
}

@Composable
private fun AlbumThumbnailBackdrop(
    thumbnail: ImageBitmap,
    contentDescription: String,
) {
    val colors = ZideeTheme.colors
    Image(
        bitmap = thumbnail,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                colors.bgCanvas.copy(alpha = 0.60f),
                                colors.bgCanvas.copy(alpha = 0.20f),
                                colors.bgCanvas.copy(alpha = 0.80f),
                            ),
                    ),
                ),
    )
}

@Composable
private fun ThumbnailOverlayTag(
    text: String,
    textColor: Color,
    isBold: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = colors.bgGlassDark,
    ) {
        Text(
            text = text,
            style = typography.metricMono,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AlbumLoadingState() {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.accentAmber,
                strokeWidth = 2.dp,
            )
            Text(
                text = "Memeriksa media permanen di album ZideeResolution...",
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AlbumEmptyState(onPickMediaClick: () -> Unit) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = colors.accentAmber,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Belum Ada Hasil Tersimpan",
                style = typography.controlLabel,
                color = colors.contrastWhite,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Pilih foto atau video untuk dioptimasi offline ke Galeri/ZideeResolution.",
                style = typography.caption,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onPickMediaClick,
                modifier = Modifier.defaultMinSize(minHeight = ZideeMinTouchTarget),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.borderActive),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentAmberBright),
            ) {
                Text(
                    text = "Pilih Media Pertama",
                    style = typography.controlLabelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DashboardBottomBar(
    activeTab: DashboardTab,
    itemCount: Int,
    onSelectTab: (DashboardTab) -> Unit,
) {
    val colors = ZideeTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.bgSurface,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DashboardBottomTabItem(
                    label = "Studio Utama",
                    selected = activeTab == DashboardTab.STUDIO_HOME,
                    onClick = { onSelectTab(DashboardTab.STUDIO_HOME) },
                    modifier = Modifier.weight(1f),
                )
                DashboardBottomTabItem(
                    label = "Album ZideeResolution ($itemCount)",
                    selected = activeTab == DashboardTab.ALBUM_GALLERY,
                    onClick = { onSelectTab(DashboardTab.ALBUM_GALLERY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DashboardBottomTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = ZideeMinTouchTarget)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.accentAmber),
            )
        }
        Text(
            text = label,
            style = typography.controlLabelSmall,
            color = if (selected) colors.accentAmberBright else colors.textSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D10, widthDp = 360, heightDp = 800)
@Composable
private fun DashboardPhonePreview() {
    val sampleItems =
        listOf(
            sampleItem(
                id = "1",
                name = "ZIDEE_VID_0941_WA.mp4",
                type = MediaType.VIDEO,
                platform = TargetPlatform.WA_STORY,
                aspect = CanvasAspectMode.BLUR_BG_9_16,
                outputSize = PixelSize(1080, 1920),
                bytes = 6_400_000L,
            ),
            sampleItem(
                id = "2",
                name = "ZIDEE_IMG_0920_IG.jpg",
                type = MediaType.PHOTO,
                platform = TargetPlatform.IG_STORY,
                aspect = CanvasAspectMode.CROP_9_16,
                outputSize = PixelSize(1080, 1920),
                bytes = 1_100_000L,
            ),
            sampleItem(
                id = "3",
                name = "ZIDEE_IMG_0855_POST.jpg",
                type = MediaType.PHOTO,
                platform = TargetPlatform.IG_POST,
                aspect = CanvasAspectMode.ORIGINAL,
                outputSize = PixelSize(1080, 1350),
                bytes = 940_000L,
            ),
            sampleItem(
                id = "4",
                name = "ZIDEE_IMG_0812_CHAT.jpg",
                type = MediaType.PHOTO,
                platform = TargetPlatform.WA_CHAT,
                aspect = CanvasAspectMode.ORIGINAL,
                outputSize = PixelSize(2048, 1536),
                bytes = 1_400_000L,
            ),
        )
    ZideeTheme {
        DashboardScreen(
            state =
                DashboardUiState(
                    isLoadingHistory = false,
                    historyItems = sampleItems,
                    preferences = UserPreferences(lastSelectedPlatform = TargetPlatform.WA_STORY),
                ),
            onEvent = {},
            onOpenPrimaryPicker = {},
        )
    }
}

private fun sampleItem(
    id: String,
    name: String,
    type: MediaType,
    platform: TargetPlatform,
    aspect: CanvasAspectMode,
    outputSize: PixelSize,
    bytes: Long,
) = OptimizedMediaItem(
    id = id,
    mediaStoreUri = MediaUri("content://media/external/file/$id"),
    displayName = name,
    mediaType = type,
    platform = platform,
    aspectMode = aspect,
    originalSize = PixelSize(3840, 2160),
    outputSize = outputSize,
    originalSizeBytes = 24_000_000L,
    outputSizeBytes = bytes,
    durationMs = if (type == MediaType.VIDEO) 30_000L else null,
    partIndex = 1,
    totalParts = 1,
    createdAtEpochMs = 1L,
)
