package com.yagay.yui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

/** Shared Material3 icon entrypoints. Features select the glyph, YUI supplies theme. */
@Composable
fun YUiIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(imageVector = imageVector, contentDescription = contentDescription,
        modifier = modifier, tint = tint)
}

@Composable
fun YUiIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painter = painter, contentDescription = contentDescription,
        modifier = modifier, tint = tint)
}

@Composable
fun YUiIcon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(bitmap = bitmap, contentDescription = contentDescription,
        modifier = modifier, tint = tint)
}
