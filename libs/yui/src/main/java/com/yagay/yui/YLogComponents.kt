package com.yagay.yui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Single configurable log viewport for all product and module diagnostic pages. */
@Composable
fun YUiLogSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val appearance = LocalYAppearance.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(appearance.fieldRadiusDp.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier = Modifier.padding((appearance.effectiveGapDp / 2f).dp),
            content = content,
        )
    }
}

/** Shared log text uses semantic status roles and the same global font scaling as other YUI text. */
@Composable
fun YUiLogLine(line: String, modifier: Modifier = Modifier) {
    val semantic = ySemanticColors()
    val upper = line.uppercase(Locale.ROOT)
    val color = when {
        listOf("FAILED", "ERROR", "STALE", "FATAL").any { upper.contains(it) } ->
            MaterialTheme.colorScheme.error
        listOf("SUCCESS", "APPLIED", "ACCEPTED", "READY").any { upper.contains(it) } ->
            semantic.success
        listOf("WAITING", "IDLE", "RUNNING", "TRIGGERED").any { upper.contains(it) } ->
            semantic.warning
        listOf("RF", "NFCID1", "NFCUIDSIM", "COMMAND").any { upper.contains(it) } ->
            semantic.info
        else -> MaterialTheme.colorScheme.onSurface
    }
    YUiText(
        text = line, modifier = modifier, color = color,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}
