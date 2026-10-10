package com.yagay.YEntryCleaner.ui

import com.yagay.yui.LocalYAppearance
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import com.yagay.yui.YUiHorizontalDivider as HorizontalDivider
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.yagay.yui.YUiTriStateCheckbox as TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.domain.ComponentCandidate
import com.yagay.YEntryCleaner.domain.IntentKind

@Composable
internal fun AppRow(
    group: AppGroup,
    selected: Set<com.yagay.YEntryCleaner.domain.ComponentRule>,
    expanded: Boolean,
    lockState: BulkLockState,
    onExpand: () -> Unit,
    onSelect: (Boolean) -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit
) {
    val selectedCount = group.components.count { it.rule in selected }
    val selectionState = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == group.components.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
    val kindTitles = mutableListOf<String>()
    for (kind in IntentKind.entries) {
        if (group.components.any { it.rule.kind == kind }) kindTitles += stringResource(kind.titleRes())
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .bulkLockSwipe(onLock = onLock, onUnlock = onUnlock)
            .clickable(onClickLabel = expandLabel, onClick = onExpand)
            .heightIn(min = LocalYAppearance.current.rowHeightDp.dp)
            .padding(horizontal = LocalYAppearance.current.effectiveGapDp.dp, vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TriStateCheckbox(state = selectionState, onClick = { onSelect(selectionState != ToggleableState.On) })
        AppIcon(bitmap = group.appIcon, appLabel = group.appLabel)
        Column(Modifier.weight(1f).padding(start = LocalYAppearance.current.effectiveGapDp.dp)) {
            Text(group.appLabel, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.app_row_selection_summary,
                    selectedCount,
                    group.components.size,
                    kindTitles.joinToString(stringResource(R.string.yentry_list_separator))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val missing = group.components.count { it.unavailable || it.restricted }
            if (missing > 0) {
                Text(
                    stringResource(R.string.app_row_unavailable_rules, missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (lockState != BulkLockState.NONE) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(
                    if (lockState == BulkLockState.PARTIAL) R.string.bulk_lock_partial
                    else R.string.bulk_lock_full
                ),
                modifier = Modifier.padding(horizontal = (LocalYAppearance.current.rowHorizontalPaddingDp / 2f).dp).size(LocalYAppearance.current.iconVisualSizeDp.dp),
                tint = if (lockState == BulkLockState.PARTIAL) MaterialTheme.colorScheme.tertiary
                else LocalContentColor.current
            )
        }
        IconButton(onClick = onExpand) {
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, expandLabel)
        }
    }
    HorizontalDivider()
}

@Composable
internal fun ComponentRow(
    item: ComponentCandidate,
    checked: Boolean,
    customTitle: String?,
    selectionNote: String? = null,
    locked: Boolean = false,
    lockToggleEnabled: Boolean = true,
    onToggle: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onEditTitle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .bulkLockSwipe(
                enabled = lockToggleEnabled,
                onLock = onLock,
                onUnlock = onUnlock
            )
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = LocalYAppearance.current.rowHeightDp.dp).padding(start = (LocalYAppearance.current.rowHorizontalPaddingDp * 1.5f).dp,
                end = LocalYAppearance.current.rowHorizontalPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ComponentSelectionMark(checked)
        Column(Modifier.weight(1f).padding(vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.activityLabel,
                    modifier = Modifier.weight(1f).padding(end = LocalYAppearance.current.effectiveGapDp.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(item.rule.kind.titleRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            selectionNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!customTitle.isNullOrBlank()) {
                Text(
                    stringResource(R.string.component_shown_as, customTitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                item.rule.className,
                modifier = Modifier.padding(top = (LocalYAppearance.current.rowVerticalPaddingDp / 3f).dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.unavailable) {
                Text(
                    stringResource(R.string.component_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (item.restricted) {
                Text(stringResource(R.string.component_restricted), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (locked) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.bulk_lock_full),
                modifier = Modifier.padding(horizontal = (LocalYAppearance.current.rowHorizontalPaddingDp / 2f).dp).size((LocalYAppearance.current.iconVisualSizeDp * 0.75f).dp)
            )
        }
        IconButton(onClick = onEditTitle) {
            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.component_edit_display_name))
        }
    }
}

@Composable
internal fun Modifier.bulkLockSwipe(
    enabled: Boolean = true,
    onLock: () -> Unit,
    onUnlock: () -> Unit
): Modifier {
    val threshold = with(LocalDensity.current) { LocalYAppearance.current.rowHeightDp.dp.toPx() }
    return pointerInput(enabled, threshold, onLock, onUnlock) {
        if (enabled) {
            var totalDrag = 0f
            detectHorizontalDragGestures(
                onDragStart = { totalDrag = 0f },
                onHorizontalDrag = { change, amount ->
                    totalDrag += amount
                    change.consume()
                },
                onDragCancel = { totalDrag = 0f },
                onDragEnd = {
                    when {
                        totalDrag >= threshold -> onLock()
                        totalDrag <= -threshold -> onUnlock()
                    }
                    totalDrag = 0f
                }
            )
        }
    }
}

@Composable
private fun ComponentSelectionMark(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(LocalYAppearance.current.iconTouchTargetDp.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(LocalYAppearance.current.iconVisualSizeDp.dp)
                .background(if (checked) colors.primary else Color.Transparent, CircleShape)
                .border(1.5.dp, if (checked) colors.primary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size((LocalYAppearance.current.iconVisualSizeDp * 0.6f).dp), tint = colors.onPrimary)
            }
        }
    }
}

@Composable
internal fun AppIcon(bitmap: Bitmap?, appLabel: String) {
    if (bitmap == null) {
        Icon(Icons.Rounded.Apps, null, Modifier.size(LocalYAppearance.current.listIconSizeDp.dp))
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.app_icon_description, appLabel),
        modifier = Modifier.size(LocalYAppearance.current.listIconSizeDp.dp).clip(MaterialTheme.shapes.small),
        contentScale = ContentScale.Fit
    )
}
