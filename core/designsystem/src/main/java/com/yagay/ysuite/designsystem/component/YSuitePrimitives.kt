package com.yagay.ysuite.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

enum class YSuiteItemKind {
    Feature,
    Folder,
    File,
    Link,
    Storage,
    Tool,
    Settings,
    Info,
    Log,
    Permission,
}

@Composable
fun YSuiteSectionLabel(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.XXSmall),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun YSuitePanel(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
    ) {
        YSuiteSectionLabel(
            title = title,
            subtitle = subtitle,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.XSmall),
        ) {
            content()
        }
    }
}

@Composable
fun YSuiteDataRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    kind: YSuiteItemKind? = null,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val container =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
        ) {
            kind?.let {
                Surface(
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    contentColor =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = it.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.XXSmall),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun YSuiteToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    YSuiteDataRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier.clickable {
            onCheckedChange(!checked)
        },
        kind = YSuiteItemKind.Settings,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
fun YSuitePrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}

@Composable
fun YSuiteActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}

@Composable
fun YSuiteModuleTile(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: YSuiteItemKind = YSuiteItemKind.Feature,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(YSuiteSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = kind.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.XSmall),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun YSuiteMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: YSuiteStatusTone = YSuiteStatusTone.Neutral,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(YSuiteSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
            )
            YSuiteStatusDot(tone = tone)
        }
    }
}

@Composable
private fun YSuiteStatusDot(
    tone: YSuiteStatusTone,
) {
    val color = when (tone) {
        YSuiteStatusTone.Positive -> MaterialTheme.colorScheme.primary
        YSuiteStatusTone.Warning -> MaterialTheme.colorScheme.tertiary
        YSuiteStatusTone.Error -> MaterialTheme.colorScheme.error
        YSuiteStatusTone.Neutral -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier.size(8.dp),
        color = color,
        shape = MaterialTheme.shapes.extraLarge,
    ) {}
}

private fun YSuiteItemKind.icon(): ImageVector =
    when (this) {
        YSuiteItemKind.Feature -> Icons.Default.Apps
        YSuiteItemKind.Folder -> Icons.Default.Folder
        YSuiteItemKind.File -> Icons.Default.Description
        YSuiteItemKind.Link -> Icons.Default.Link
        YSuiteItemKind.Storage -> Icons.Default.Storage
        YSuiteItemKind.Tool -> Icons.Default.Build
        YSuiteItemKind.Settings -> Icons.Default.Settings
        YSuiteItemKind.Info -> Icons.Default.Info
        YSuiteItemKind.Log -> Icons.Default.Article
        YSuiteItemKind.Permission -> Icons.Default.Security
    }
