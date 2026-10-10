package com.yagay.yui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared progress behavior and theme across every feature. */
@Composable
fun YUiLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    LinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)
}

@Composable
fun YUiLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    LinearProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor)
}

@Composable
fun YUiCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
) {
    CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
}

@Composable
fun YUiCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
) {
    CircularProgressIndicator(progress = progress, modifier = modifier, color = color, strokeWidth = strokeWidth)
}
