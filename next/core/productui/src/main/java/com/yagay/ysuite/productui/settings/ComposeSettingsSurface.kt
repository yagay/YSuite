package com.yagay.ysuite.productui.settings

import com.yagay.ysuite.productui.ProductSurfaceKind

import com.yagay.ysuite.productui.ProductLayoutTokens
import com.yagay.ysuite.productui.ProductPaneAdaptiveBox

/*
 * Interaction structure adapted from alorma/Compose-Settings (MIT).
 * Original project: https://github.com/alorma/Compose-Settings
 * YSuite changes: local theme tokens, adaptive category pane and simplified API.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YListItem
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.YSuiteProductPage

@Composable
fun ComposeSettingsSurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    categoryPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable ColumnScope.(ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductPage(
        surfaceKind = ProductSurfaceKind.Settings,
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    ) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded && categoryPane != null) {
                Surface(
                    modifier =
                        Modifier
                            .width(ProductLayoutTokens.SettingsCategoryPaneWidth)
                            .fillMaxHeight(),
                    color =
                        MaterialTheme.colorScheme
                            .surfaceContainerLow,
                ) {
                    categoryPane(adaptive)
                }
            }
            ProductPaneAdaptiveBox(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) { paneAdaptive ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .widthIn(
                                    max =
                                        ProductLayoutTokens
                                            .SettingsContentMaxWidth,
                                )
                                .fillMaxWidth()
                                .verticalScroll(
                                    rememberScrollState(),
                                )
                                .padding(
                                    horizontal =
                                        YSuiteSpacing.Medium,
                                    vertical =
                                        YSuiteSpacing.Large,
                                ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                YSuiteSpacing.Medium,
                            ),
                    ) {
                        content(paneAdaptive)
                    }
                }
            }
        }
    }
}

@Composable
fun ComposeSettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
            )
        }
        content()
    }
}

@Composable
fun ComposeSettingsSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    YSwitchItem(
        title = title,
        subtitle = subtitle,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
fun ComposeSettingsLink(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    YListItem(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        trailing = action,
        onClick = onClick,
    )
}

data class ComposeSettingsChoice(
    val id: String,
    val label: String,
)

@Composable
fun ComposeSettingsChoiceGroup(
    title: String,
    selectedId: String,
    choices: List<ComposeSettingsChoice>,
    onSelected: (String) -> Unit,
) {
    ComposeSettingsGroup(title = title) {
        choices.forEach { choice ->
            YListItem(
                title = choice.label,
                selected = choice.id == selectedId,
                onClick = { onSelected(choice.id) },
                leading = {
                    RadioButton(
                        selected = choice.id == selectedId,
                        onClick = null,
                    )
                },
            )
        }
    }
}


@Composable
fun ComposeSettingsIntSlider(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    subtitle: String? = null,
) {
    val safeValue = value.coerceIn(range.first, range.last)
    Column(modifier = Modifier.fillMaxWidth()) {
        YListItem(
            title = title,
            subtitle = subtitle,
            trailing = {
                Text(
                    text = safeValue.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
        Slider(
            value = safeValue.toFloat(),
            onValueChange = {
                onValueChange(
                    it.toInt().coerceIn(
                        range.first,
                        range.last,
                    ),
                )
            },
            valueRange =
                range.first.toFloat()..
                    range.last.toFloat(),
            steps =
                (range.last - range.first - 1)
                    .coerceAtLeast(0),
            modifier =
                Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                ),
        )
    }
}
