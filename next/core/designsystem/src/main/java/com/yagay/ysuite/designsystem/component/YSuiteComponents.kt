package com.yagay.ysuite.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.yui.YCard
import com.yagay.yui.YDivider
import com.yagay.yui.YListItem
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSectionTitle
import com.yagay.yui.YSwitchItem

/** Rebuilt-feature compatibility facade. Visuals are rendered only by the shared YUI library. */
@Composable
fun YSuiteSectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        YSectionTitle(title = title, subtitle = subtitle)
    }
}

@Composable
fun YSuiteSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        YSuiteSectionHeader(title)
        YCard {
            Column(content = content)
        }
    }
}

@Composable
fun YSuiteListItem(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    YListItem(title = title, subtitle = subtitle, modifier = modifier, leading = leading, trailing = trailing)
}

@Composable
fun YSuiteSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    YSwitchItem(title = title, subtitle = subtitle, checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
fun YSuitePrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    YPrimaryButton(text = text, onClick = onClick, modifier = modifier)
}

@Composable
fun YSuiteSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    YSecondaryButton(text = text, onClick = onClick, modifier = modifier)
}

@Composable
fun YSuiteDivider() {
    YDivider()
}
