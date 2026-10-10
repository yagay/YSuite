package com.yagay.ysuite.productui.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiOutlinedTextField as OutlinedTextField
import com.yagay.yui.YUiSlider as Slider
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import com.yagay.yui.LocalYAppearance
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.yagay.yui.YPrimaryActionButton
import com.yagay.yui.YSecondaryActionButton
import com.yagay.yui.YTextActionButton
import com.yagay.yui.YResponsiveFieldAction
import com.yagay.yui.YResponsiveFieldPair
import com.yagay.yui.YResponsiveActionBar
import com.yagay.yui.YUiAlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import androidx.compose.ui.unit.dp

data class QdmDownloadRowModel(
    val id: String,
    val fileName: String,
    val sizeProgressText: String,
    val stateText: String,
    val speedEtaText: String?,
    val progress: Float?,
    val canPause: Boolean,
    val canResume: Boolean,
    val canCancel: Boolean,
    val canOpen: Boolean,
    val canRetry: Boolean,
    val canRemove: Boolean,
)

data class QdmDownloadActionLabels(
    val pause: String,
    val resume: String,
    val cancel: String,
    val open: String,
    val retry: String,
    val remove: String,
    val more: String,
    val share: String,
    val copyLink: String,
    val openFolder: String,
    val properties: String,
    val redownload: String,
)

@Composable
fun QdmDownloadList(
    items: List<QdmDownloadRowModel>,
    emptyText: String,
    labels: QdmDownloadActionLabels,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onProperties: (String) -> Unit,
    onRedownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(YSuiteSpacing.XLarge),
            )
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(
            items = items,
            key = { it.id },
        ) { item ->
            QdmDownloadItemRow(
                item = item,
                labels = labels,
                onPause = { onPause(item.id) },
                onResume = { onResume(item.id) },
                onCancel = { onCancel(item.id) },
                onOpen = { onOpen(item.id) },
                onRetry = { onRetry(item.id) },
                onRemove = { onRemove(item.id) },
                onShare = { onShare(item.id) },
                onCopyLink = { onCopyLink(item.id) },
                onOpenFolder = { onOpenFolder(item.id) },
                onProperties = { onProperties(item.id) },
                onRedownload = { onRedownload(item.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun QdmDownloadItemRow(
    item: QdmDownloadRowModel,
    labels: QdmDownloadActionLabels,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenFolder: () -> Unit,
    onProperties: () -> Unit,
    onRedownload: () -> Unit,
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = item.canOpen,
                    onClick = onOpen,
                )
                .padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    if (item.canRetry) {
                        Icons.Default.Error
                    } else {
                        Icons.Default.Download
                    },
                contentDescription = null,
                tint =
                    if (item.canRetry) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.size(LocalYAppearance.current.iconVisualSizeDp.dp),
            )
            Spacer(Modifier.width(YSuiteSpacing.Medium))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(YSuiteSpacing.XSmall),
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.sizeProgressText,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(YSuiteSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.stateText,
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    item.speedEtaText?.let {
                        Text(
                            text = it,
                            style =
                                MaterialTheme.typography.labelMedium,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item.progress?.let {
                    LinearProgressIndicator(
                        progress = { it.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Box {
                IconButton(
                    onClick = {
                        menuExpanded = true
                    },
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = labels.more,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                    },
                ) {
                    if (item.canPause) {
                        QdmMenuItem(
                            text = labels.pause,
                            icon = {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onPause()
                            },
                        )
                    }
                    if (item.canResume) {
                        QdmMenuItem(
                            text = labels.resume,
                            icon = {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onResume()
                            },
                        )
                    }
                    if (item.canRetry) {
                        QdmMenuItem(
                            text = labels.retry,
                            icon = {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRetry()
                            },
                        )
                    }
                    if (item.canOpen) {
                        QdmMenuItem(
                            text = labels.open,
                            icon = {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            },
                        )
                    }
                    if (item.canOpen) {
                        QdmMenuItem(
                            text = labels.share,
                            icon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                        )
                        QdmMenuItem(
                            text = labels.openFolder,
                            icon = {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onOpenFolder()
                            },
                        )
                    }
                    QdmMenuItem(
                        text = labels.copyLink,
                        icon = {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onCopyLink()
                        },
                    )
                    QdmMenuItem(
                        text = labels.redownload,
                        icon = {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRedownload()
                        },
                    )
                    QdmMenuItem(
                        text = labels.properties,
                        icon = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onProperties()
                        },
                    )
                    if (item.canCancel) {
                        QdmMenuItem(
                            text = labels.cancel,
                            icon = {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onCancel()
                            },
                        )
                    }
                    if (item.canRemove) {
                        QdmMenuItem(
                            text = labels.remove,
                            icon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QdmMenuItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = icon,
        onClick = onClick,
    )
}

data class QdmAddDownloadModel(
    val url: String,
    val fileName: String,
    val referer: String,
    val userAgent: String,
    val cookies: String,
    val username: String,
    val password: String,
    val destinationText: String,
    val threadCount: Int,
    val threadSelectionEnabled: Boolean,
    val speedLimitBytesPerSecond: Long,
    val customHeadersText: String,
    val scheduleText: String,
    val hasSchedule: Boolean,
    val metadataText: String?,
    val loading: Boolean,
    val error: String?,
)

data class QdmAddDownloadLabels(
    val title: String,
    val url: String,
    val fileName: String,
    val referer: String,
    val userAgent: String,
    val cookies: String,
    val username: String,
    val password: String,
    val destination: String,
    val chooseFolder: String,
    val useDefaultFolder: String,
    val threads: String,
    val speedLimit: String,
    val customHeaders: String,
    val schedule: String,
    val clearSchedule: String,
    val fetch: String,
    val addQueue: String,
    val start: String,
    val cancel: String,
)

@Composable
fun QdmAddDownloadDialog(
    model: QdmAddDownloadModel,
    labels: QdmAddDownloadLabels,
    onUrlChange: (String) -> Unit,
    onFileNameChange: (String) -> Unit,
    onRefererChange: (String) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onCookiesChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onUseDefaultFolder: () -> Unit,
    onThreadCountChange: (Int) -> Unit,
    onSpeedLimitChange: (Long) -> Unit,
    onCustomHeadersChange: (String) -> Unit,
    onChooseSchedule: () -> Unit,
    onClearSchedule: () -> Unit,
    onFetch: () -> Unit,
    onAddQueue: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    var speedLimitDraft by remember {
        mutableStateOf((model.speedLimitBytesPerSecond / 1024L).toString())
    }
    var speedLimitFocused by remember { mutableStateOf(false) }
    LaunchedEffect(model.speedLimitBytesPerSecond, speedLimitFocused) {
        if (!speedLimitFocused) {
            speedLimitDraft = (model.speedLimitBytesPerSecond / 1024L).toString()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(YSuiteSpacing.Medium)
                .heightIn(max = maxHeight - (LocalYAppearance.current.effectiveGapDp * 2f).dp),
            shape = RoundedCornerShape(LocalYAppearance.current.dialogRadiusDp.dp),
            tonalElevation = YSuiteSpacing.XSmall,
        ) {
            Column(
                modifier = Modifier.padding(YSuiteSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text = labels.title,
                        style =
                            MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    YTextActionButton(onClick = onDismiss) {
                        Text(labels.cancel)
                    }
                }

                // Long form scrolls separately from its title and actions.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
                ) {
                OutlinedTextField(
                    value = model.url,
                    onValueChange = onUrlChange,
                    label = { Text(labels.url) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                YResponsiveFieldAction(
                    field = { fieldModifier ->
                        OutlinedTextField(
                            value = model.fileName,
                            onValueChange = onFileNameChange,
                            label = { Text(labels.fileName) },
                            singleLine = true,
                            modifier = fieldModifier,
                        )
                    },
                    action = {
                        YSecondaryActionButton(
                            onClick = onFetch,
                            enabled = model.url.isNotBlank() && !model.loading,
                        ) {
                            Text(labels.fetch)
                        }
                    },
                )

                model.metadataText?.let {
                    Text(
                        text = it,
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
                model.error?.let {
                    Text(
                        text = it,
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            YSuiteSpacing.Small,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = labels.destination,
                            style =
                                MaterialTheme.typography.labelSmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        )
                        Text(
                            text = model.destinationText,
                            style =
                                MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    YSecondaryActionButton(
                        onClick = onChooseFolder,
                    ) {
                        Text(labels.chooseFolder)
                    }
                }
                YTextActionButton(
                    onClick = onUseDefaultFolder,
                ) {
                    Text(labels.useDefaultFolder)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            labels.threads +
                                ": " +
                                model.threadCount,
                        style =
                            MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value =
                            model.threadCount
                                .toFloat(),
                        onValueChange = {
                            onThreadCountChange(
                                it.toInt()
                                    .coerceIn(1, 16),
                            )
                        },
                        valueRange = 1f..16f,
                        steps = 14,
                        enabled =
                            model.threadSelectionEnabled,
                    )
                }

                OutlinedTextField(
                    value = speedLimitDraft,
                    onValueChange = { raw ->
                        speedLimitDraft = raw.filter(Char::isDigit)
                        val kbps = speedLimitDraft.toLongOrNull() ?: 0L
                        onSpeedLimitChange(
                            kbps.coerceAtMost(Long.MAX_VALUE / 1024L) * 1024L,
                        )
                    },
                    label = { Text(labels.speedLimit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        speedLimitFocused = it.isFocused
                    },
                )

                OutlinedTextField(
                    value = model.customHeadersText,
                    onValueChange =
                        onCustomHeadersChange,
                    label = {
                        Text(labels.customHeaders)
                    },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            YSuiteSpacing.Small,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = labels.schedule,
                            style =
                                MaterialTheme.typography.labelSmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        )
                        Text(
                            text = model.scheduleText,
                            style =
                                MaterialTheme.typography.bodySmall,
                        )
                    }
                    YSecondaryActionButton(
                        onClick = onChooseSchedule,
                    ) {
                        Text(labels.schedule)
                    }
                    if (model.hasSchedule) {
                        YTextActionButton(
                            onClick = onClearSchedule,
                        ) {
                            Text(labels.clearSchedule)
                        }
                    }
                }

                OutlinedTextField(
                    value = model.referer,
                    onValueChange = onRefererChange,
                    label = { Text(labels.referer) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model.userAgent,
                    onValueChange =
                        onUserAgentChange,
                    label = {
                        Text(labels.userAgent)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model.cookies,
                    onValueChange = onCookiesChange,
                    label = { Text(labels.cookies) },
                    modifier = Modifier.fillMaxWidth(),
                )
                YResponsiveFieldPair(
                    first = { fieldModifier ->
                        OutlinedTextField(
                            value = model.username,
                            onValueChange = onUsernameChange,
                            label = { Text(labels.username) },
                            singleLine = true,
                            modifier = fieldModifier,
                        )
                    },
                    second = { fieldModifier ->
                        OutlinedTextField(
                            value = model.password,
                            onValueChange = onPasswordChange,
                            label = { Text(labels.password) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = fieldModifier,
                        )
                    },
                )

                }
                YResponsiveActionBar {
                    YSecondaryActionButton(
                        onClick = onAddQueue,
                        enabled =
                            model.url.isNotBlank() &&
                                model.fileName
                                    .isNotBlank(),
                    ) {
                        Text(labels.addQueue)
                    }
                    YPrimaryActionButton(
                        onClick = onStart,
                        enabled =
                            model.url.isNotBlank() &&
                                model.fileName
                                    .isNotBlank(),
                    ) {
                        Text(labels.start)
                    }
                }
            }
        }
        }
    }
}


data class QdmDownloadProperty(
    val label: String,
    val value: String,
)

@Composable
fun QdmDownloadPropertiesDialog(
    title: String,
    properties: List<QdmDownloadProperty>,
    closeLabel: String,
    onDismiss: () -> Unit,
) {
    YUiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                properties.forEach { property ->
                    Column {
                        Text(
                            text = property.label,
                            style =
                                MaterialTheme.typography.labelSmall,
                            color =
                                MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = property.value,
                            style =
                                MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            YTextActionButton(onClick = onDismiss) {
                Text(closeLabel)
            }
        },
    )
}
