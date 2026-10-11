package com.yagay.yui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One default icon rule. Feature-supplied layout/size modifiers remain authoritative
 * for application icons, thumbnails, previews and other non-standard content.
 * Unmodified action icons use the globally configured YUI visual size.
 */
@Composable
private fun yIconModifier(modifier: Modifier): Modifier =
    if (modifier == Modifier) Modifier.size(YUiGeometry.displayRules().iconSizeDp.dp) else modifier
@Composable
fun YUiIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(imageVector = imageVector, contentDescription = contentDescription,
        modifier = yIconModifier(modifier), tint = tint)
}

@Composable
fun YUiIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painter = painter, contentDescription = contentDescription,
        modifier = yIconModifier(modifier), tint = tint)
}

@Composable
fun YUiIcon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(bitmap = bitmap, contentDescription = contentDescription,
        modifier = yIconModifier(modifier), tint = tint)
}
