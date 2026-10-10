package com.yagay.yui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * YUI content-adaptive field/action layout.
 * The business feature supplies the input and action; only responsive geometry lives here.
 */
@Composable
fun YResponsiveFieldAction(
    modifier: Modifier = Modifier,
    stackedBelow: Dp = 440.dp,
    field: @Composable (Modifier) -> Unit,
    action: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < stackedBelow) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
                horizontalAlignment = Alignment.End,
            ) {
                field(Modifier.fillMaxWidth())
                action()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                field(Modifier.weight(1f))
                action()
            }
        }
    }
}

/** Two full-size fields in wide content; stacked full-width controls in narrow content. */
@Composable
fun YResponsiveFieldPair(
    modifier: Modifier = Modifier,
    stackedBelow: Dp = 520.dp,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < stackedBelow) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
            ) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
            ) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

// Long localized action labels can wrap without shrinking sibling buttons to zero.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YResponsiveActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
    ) {
        content()
    }
}
