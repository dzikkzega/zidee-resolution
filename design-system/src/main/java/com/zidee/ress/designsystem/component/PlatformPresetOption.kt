package com.zidee.ress.designsystem.component

import androidx.compose.runtime.Immutable

@Immutable
data class PlatformPresetOption(
    val id: String,
    val label: String,
    val contentDescription: String = label,
)
