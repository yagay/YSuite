package com.yagay.ysuite.productui.settings

import com.yagay.ysuite.productui.ProductSurfaceKind

import com.yagay.ysuite.productui.ProductLayoutTokens
import com.yagay.ysuite.productui.ProductPaneAdaptiveBox
import com.yagay.ysuite.productui.fitPanes
import androidx.compose.ui.unit.dp

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
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiRadioButton as RadioButton
import com.yagay.yui.YUiSlider as Slider
import com.yagay.yui.YUiSurface as Surface
import com.yagay.yui.YUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yagay.yui.LocalYAppearance
import com.yagay.yui.YUiGeometry
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YListItem
import com.yagay.yui.YIntSliderField
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
        val panes = adaptive.fitPanes(
            leading = if (categoryPane != null) ProductLayoutTokens.SettingsCategoryPaneWidth else 0.dp,
        )
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded && categoryPane != null && panes.hasLeading) {
                Surface(
                    modifier =
                        Modifier
                            .width(panes.leading)
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
                                    horizontal = YUiGeometry.pagePadding(),
                                    vertical = YUiGeometry.pageVerticalPadding(),
                                ),
                        verticalArrangement =
                            Arrangement.spacedBy(YUiGeometry.sectionGap()),
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
    val appearance = LocalYAppearance.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(appearance.cardRadiusDp.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            appearance.cardOutlineWidthDp.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = YUiGeometry.compactGap()),
            verticalArrangement = Arrangement.spacedBy(YUiGeometry.compactGap()),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = appearance.rowHorizontalPaddingDp.dp,
                        vertical = YUiGeometry.compactGap(),
                    ),
                )
            }
            content()
        }
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
) = YIntSliderField(
    title = title,
    value = value,
    range = range,
    onValueChange = onValueChange,
    subtitle = subtitle,
)
