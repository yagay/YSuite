package com.yagay.YEntryCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.yagay.yui.YUiTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.domain.BrowserLinkConfig
import com.yagay.YEntryCleaner.domain.normalizeBrowserHost
import com.yagay.yui.YActionSpec
import com.yagay.yui.YActionStyle
import com.yagay.yui.YEmptyMessage
import com.yagay.yui.YFormDialog
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSearchField
import com.yagay.yui.YTextField
import com.yagay.yui.YSettingRow

/** Compact BROWSER-domain selector shown inside the shared top filter row. */
@Composable
fun BrowserHostFilterMenu(
    selected: String?,
    config: BrowserLinkConfig,
    availableHosts: Set<String> = config.hosts,
    onSelected: (String?) -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var hostQuery by remember { mutableStateOf("") }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val menuWidth = 296.dp
    val menuHorizontalOffset = with(density) {
        ((anchorWidthPx.toDp() - menuWidth) / 2)
    }
    val menuScroll = rememberScrollState()
    val selectedTitle = selected ?: stringResource(R.string.common_all)
    val visibleHosts = remember(availableHosts, hostQuery) {
        val query = hostQuery.trim()
        availableHosts.asSequence()
            .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
            .sorted()
            .toList()
    }

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.onSizeChanged { anchorWidthPx = it.width },
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            Text(
                stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.browser_domain_filter),
                    selectedTitle
                ),
                modifier = Modifier.widthIn(max = 170.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Rounded.ExpandMore, null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { hostQuery = ""; expanded = false },
            modifier = Modifier
                .width(menuWidth)
                .heightIn(max = 420.dp),
            offset = DpOffset(menuHorizontalOffset, 0.dp),
            scrollState = menuScroll
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clickable {
                        hostQuery = ""
                        expanded = false
                        onSelected(null)
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (selected == null) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(stringResource(R.string.common_all))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clickable {
                        hostQuery = ""
                        expanded = false
                        onManage()
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(24.dp))
                Text(stringResource(R.string.browser_hosts_manage))
            }
            HorizontalDivider()
            YSearchField(
                value = hostQuery,
                onValueChange = { hostQuery = it },
                hint = stringResource(R.string.browser_domain_search),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            if (visibleHosts.isEmpty()) {
                YEmptyMessage(
                    message = stringResource(R.string.browser_domain_search_empty),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                visibleHosts.forEach { host ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable {
                                hostQuery = ""
                                expanded = false
                                onSelected(host)
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (selected == host) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            host,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrowserHostDialog(
    config: BrowserLinkConfig,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var hosts by remember(config.hosts) { mutableStateOf(config.hosts.sorted()) }
    var input by remember { mutableStateOf("") }
    val normalized = normalizeBrowserHost(input)
    val canAdd = normalized != null && normalized !in hosts && hosts.size < BrowserLinkConfig.MAX_HOSTS

    YFormDialog(
        title = stringResource(R.string.browser_hosts_title),
        onDismissRequest = onDismiss,
        actions = listOf(
            YActionSpec(
                label = stringResource(R.string.common_cancel),
                onClick = onDismiss,
            ),
            YActionSpec(
                label = stringResource(R.string.common_save),
                style = YActionStyle.PRIMARY,
                onClick = { onSave(hosts.toSet()); onDismiss() },
            ),
        ),
    ) {
        Text(
            stringResource(R.string.browser_hosts_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            YTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.browser_host_input),
                supportingText = stringResource(R.string.browser_host_example),
                errorText = if (input.isNotBlank() && normalized == null) {
                    stringResource(R.string.browser_host_invalid)
                } else null,
            )
            Spacer(Modifier.width(8.dp))
            YPrimaryButton(
                text = stringResource(R.string.common_add),
                enabled = canAdd,
                onClick = {
                    val host = normalized ?: return@YPrimaryButton
                    hosts = (hosts + host).distinct().sorted()
                    input = ""
                },
            )
        }
        if (hosts.isEmpty()) {
            YEmptyMessage(message = stringResource(R.string.browser_hosts_empty))
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(hosts, key = { it }) { host ->
                    YSettingRow(title = host) {
                        IconButton(onClick = { hosts = hosts - host }) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
