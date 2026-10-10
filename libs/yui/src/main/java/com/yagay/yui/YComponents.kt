package com.yagay.yui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun YScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = LocalYAppearance.current.screenPaddingDp.dp,
        vertical = YDimens.ScreenVertical,
    ),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(LocalYAppearance.current.sectionSpacingDp.dp),
        content = content,
    )
}

/** All sections are rendered by the role-aware YSection in YUnifiedDesign.kt. */
@Composable
fun YStatusCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    YCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (!detail.isNullOrBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Legacy settings-row API routed to the same content and sizing as YListItem. */
@Composable
fun YSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    YListItem(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        trailing = { Row { trailing() } },
    )
}

/** Legacy switch-row API routed through the canonical setting row. */
@Composable
fun YSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) = YSwitchItem(title, checked, onCheckedChange, modifier, subtitle, enabled)

@Composable
fun YBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = YDimens.ScreenHorizontal, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(LocalYAppearance.current.effectiveGapDp.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun YLoadingState(text: String? = null, modifier: Modifier = Modifier) {
    val resolvedText = text ?: stringResource(R.string.yui_loading)
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator()
        Text(resolvedText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun YErrorState(message: String, modifier: Modifier = Modifier) {
    YNotice(text = message, modifier = modifier, tone = YNoticeTone.ERROR)
}
