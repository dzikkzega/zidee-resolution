package com.zidee.ress.designsystem.component

import androidx.compose.ui.unit.dp

/**
 * Semantic test tags and visual geometry constants synced with FigCraft frame `#119`:
 * - White divider line `#142` = `2dp`
 * - Amber circular handle `#143` = `40dp × 40dp` inside a `>= 48dp` (`ZideeMinTouchTarget`) hit area.
 */
object SplitSliderSpec {
    val DividerLineWidth = 2.dp
    val HandleVisualSize = 40.dp
    const val TAG_ROOT = "split_slider_root"
    const val TAG_DIVIDER_LINE = "split_slider_divider_line"
    const val TAG_HANDLE_HIT_AREA = "split_slider_handle_hit_area"
    const val TAG_HANDLE_VISUAL = "split_slider_handle_visual"
}
