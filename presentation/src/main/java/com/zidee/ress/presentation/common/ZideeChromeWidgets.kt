package com.zidee.ress.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zidee.ress.designsystem.theme.ZideeMinTouchTarget
import com.zidee.ress.designsystem.theme.ZideeTheme

@Composable
fun MiniLogoBadge(
    versionLabel: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ZideeTheme.colors.bgSurface,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = modifier.size(38.dp),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        border = BorderStroke(1.5.dp, colors.borderActive),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "ZR",
                color = colors.accentAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 14.sp,
            )
            Text(
                text = versionLabel,
                style = typography.metricBadge,
                color = colors.contrastWhite,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 9.sp,
            )
        }
    }
}

@Composable
fun OfflineEnginePill(
    text: String,
    modifier: Modifier = Modifier,
    isAmber: Boolean = false,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val fill = if (isAmber) colors.accentAmberSoft else colors.statusSuccessSoft
    val border =
        if (isAmber) {
            colors.borderActive
        } else {
            colors.statusSuccess.copy(alpha = 0.40f)
        }
    val textColor = if (isAmber) colors.accentAmberBright else colors.statusSuccess

    Surface(
        modifier = modifier.defaultMinSize(minHeight = 26.dp),
        shape = RoundedCornerShape(13.dp),
        color = fill,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = typography.metricBadge,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PermanentFolderBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.statusSuccess.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = colors.statusSuccess,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = typography.metricBadge,
                color = colors.statusSuccess,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ErrorAlertBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZideeTheme.colors
    val typography = ZideeTheme.typography
    val shapes = ZideeTheme.shapes
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shapes.control,
        color = colors.statusDangerSoft,
        border = BorderStroke(1.dp, colors.statusDanger.copy(alpha = 0.50f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = typography.controlLabelSmall,
                color = colors.contrastWhite,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(ZideeMinTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup pesan galat",
                    tint = colors.statusDanger,
                )
            }
        }
    }
}
