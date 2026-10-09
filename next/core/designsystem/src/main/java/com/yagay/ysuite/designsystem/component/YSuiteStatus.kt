package com.yagay.ysuite.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

enum class YSuiteStatusTone {
    Positive,
    Warning,
    Error,
    Neutral,
}

@Composable
fun YSuiteStatusBadge(
    text: String,
    tone: YSuiteStatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = when (tone) {
        YSuiteStatusTone.Positive ->
            MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        YSuiteStatusTone.Warning ->
            MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer
        YSuiteStatusTone.Error ->
            MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
        YSuiteStatusTone.Neutral ->
            MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        color = colors.first,
        contentColor = colors.second,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = YSuiteSpacing.Small,
                vertical = YSuiteSpacing.XSmall,
            ),
        )
    }
}
