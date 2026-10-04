package com.yagay.ysuite.productui.settings

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSettingsSurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    categoryPane: @Composable (ProductAdaptiveInfo) -> Unit = {},
    content: @Composable ColumnScope.(ProductAdaptiveInfo) -> Unit,
) {
    ProductAdaptiveBox(modifier = modifier.fillMaxSize()) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded) {
                Surface(
                    modifier = Modifier
                        .width(264.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    categoryPane(adaptive)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = navigationIcon,
                    actions = actions,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = YSuiteSpacing.Medium,
                                vertical = YSuiteSpacing.Large,
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(YSuiteSpacing.Large),
                    ) {
                        content(adaptive)
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
    ListItem(
        headlineContent = { Text(title) },
        supportingContent =
            subtitle?.let { value ->
                { Text(value) }
            },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
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
    ListItem(
        headlineContent = { Text(title) },
        supportingContent =
            subtitle?.let { value ->
                { Text(value) }
            },
        trailingContent = action,
        modifier = Modifier.clickable(
            enabled = enabled,
            onClick = onClick,
        ),
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
            ListItem(
                headlineContent = { Text(choice.label) },
                leadingContent = {
                    RadioButton(
                        selected = choice.id == selectedId,
                        onClick = null,
                    )
                },
                modifier = Modifier.clickable {
                    onSelected(choice.id)
                },
            )
        }
    }
}
