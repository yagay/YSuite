package com.yagay.yui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun YScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = YDimens.ScreenHorizontal,
        vertical = YDimens.ScreenVertical,
    ),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(YDimens.SectionGap),
        content = content,
    )
}

@Composable
fun YSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap)) {
        YSectionTitle(title, subtitle)
        content()
    }
}

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

@Composable
fun YSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
    ) {
        Column(Modifier.weight(1f)) { YSettingCopy(title, subtitle) }
        trailing()
    }
}

@Composable
private fun YSettingCopy(title: String, subtitle: String?) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    if (!subtitle.isNullOrBlank()) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun YSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    YSettingRow(title = title, subtitle = subtitle, modifier = modifier) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun YBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = YDimens.ScreenHorizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap, Alignment.End),
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
