package com.yagay.yui

import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared home ordering and one persisted pin set for both YSuite host implementations. */
object YHomePresentation {
    const val PREFERENCES = "ysuite_compact_home"
    private const val PINS = "pinned_ids"
    private const val LEGACY_PINS = "pinned_modules"

    fun orderedIds(ids: List<String>, pinned: Set<String>): List<String> =
        ids.filter { it in pinned } + ids.filterNot { it in pinned }

    fun readPinned(prefs: SharedPreferences): Set<String> {
        val primary = prefs.getStringSet(PINS, null).orEmpty()
        val legacy = prefs.getStringSet(LEGACY_PINS, null).orEmpty()
        val combined = (primary + legacy).toSet()
        if (legacy.isNotEmpty()) savePinned(prefs, combined)
        return combined
    }

    fun savePinned(prefs: SharedPreferences, ids: Set<String>) {
        prefs.edit().putStringSet(PINS, ids.toSet()).remove(LEGACY_PINS).apply()
    }
}

/** Single home-row geometry, swipe behavior, typography and pin icon for both hosts. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YHomeModuleRow(
    title: String,
    subtitle: String?,
    pinned: Boolean,
    swipePinEnabled: Boolean,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    pinnedDescription: String?,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val appearance = LocalYAppearance.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = appearance.rowHeightDp.dp)
            .then(if (swipePinEnabled) Modifier.pointerInput(pinned) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onDragEnd = {
                        if ((dragDistance > 100f && !pinned) || (dragDistance < -100f && pinned)) {
                            onTogglePin()
                        }
                    },
                    onHorizontalDrag = { change, delta ->
                        dragDistance += delta
                        change.consume()
                    },
                )
            } else Modifier)
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(
                horizontal = appearance.rowHorizontalPaddingDp.dp,
                vertical = appearance.rowVerticalPaddingDp.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appearance.effectiveGapDp.dp),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            YUiText(text = title, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                YUiText(text = subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (pinned) {
            YUiIcon(
                imageVector = Icons.Default.Star,
                contentDescription = pinnedDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((appearance.iconVisualSizeDp * 0.75f).dp),
            )
        }
        trailing()
    }
}
