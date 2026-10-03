package com.yagay.yui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun YExpandableItem(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().animateContentSize()) {
        YListItem(
            title = title,
            subtitle = subtitle,
            selected = expanded,
            onClick = { onExpandedChange(!expanded) },
        )
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.fillMaxWidth().padding(
                    start = YDimens.ScreenHorizontal,
                    end = YDimens.ScreenHorizontal,
                    bottom = YDimens.ControlGap,
                ),
                verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
            ) { content() }
        }
    }
}

@Composable
fun YTreeItem(
    title: String,
    depth: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    YListItem(
        title = title,
        subtitle = subtitle,
        modifier = modifier.padding(start = (depth.coerceAtLeast(0) * 16).dp),
        selected = selected,
        onClick = onClick,
        trailing = trailing,
    )
}

@Composable
fun YGridCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(YDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (content != null) content()
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Immutable
data class YPageActionLabels(
    val previous: String,
    val next: String,
)

@Composable
fun YPagerActions(
    page: Int,
    pageCount: Int,
    labels: YPageActionLabels,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YSecondaryButton(
            text = labels.previous,
            onClick = { onPageChange((page - 1).coerceAtLeast(0)) },
            enabled = page > 0,
        )
        Text(
            stringResource(R.string.yui_page_counter, page.coerceAtLeast(0) + 1, pageCount.coerceAtLeast(1)),
            style = MaterialTheme.typography.labelLarge,
        )
        YPrimaryButton(
            text = labels.next,
            onClick = { onPageChange((page + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))) },
            enabled = page + 1 < pageCount,
        )
    }
}

@Composable
fun YReorderActions(
    upLabel: String,
    downLabel: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YActionGroup(
        modifier = modifier,
        actions = listOf(
            YActionSpec(upLabel, canMoveUp, YActionStyle.SECONDARY, onMoveUp),
            YActionSpec(downLabel, canMoveDown, YActionStyle.SECONDARY, onMoveDown),
        ),
    )
}

/** Shared placeholder for lists while their business layer is loading. */
@Composable
fun YListSkeleton(
    rows: Int = 4,
    modifier: Modifier = Modifier,
) {
    val placeholder = stringResource(R.string.yui_placeholder)
    Column(modifier.fillMaxWidth()) {
        repeat(rows.coerceIn(1, 12)) { index ->
            YListItem(
                title = placeholder,
                subtitle = if (index % 2 == 0) placeholder else null,
                enabled = false,
            )
        }
    }
}
