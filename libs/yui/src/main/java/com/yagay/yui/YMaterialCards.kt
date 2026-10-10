package com.yagay.yui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Card shapes are global YUI settings; all rendering/semantics stay upstream.
 * Feature-specific data/content is deliberately outside this presentation layer.
 */
@Composable
fun YUiCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier, shape = shape ?: RoundedCornerShape(LocalYAppearance.current.cardRadiusDp.dp),
        colors = colors, elevation = elevation, border = border, content = content)
}

@Composable
fun YUiCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = shape ?: RoundedCornerShape(LocalYAppearance.current.cardRadiusDp.dp),
        colors = colors, elevation = elevation, border = border,
        interactionSource = interactionSource, content = content)
}

@Composable
fun YUiElevatedCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    elevation: CardElevation = CardDefaults.elevatedCardElevation(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = modifier, shape = shape ?: RoundedCornerShape(LocalYAppearance.current.cardRadiusDp.dp),
        colors = colors, elevation = elevation, content = content)
}

@Composable
fun YUiOutlinedCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    elevation: CardElevation = CardDefaults.outlinedCardElevation(),
    border: BorderStroke = CardDefaults.outlinedCardBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier = modifier, shape = shape ?: RoundedCornerShape(LocalYAppearance.current.cardRadiusDp.dp),
        colors = colors, elevation = elevation, border = border, content = content)
}
