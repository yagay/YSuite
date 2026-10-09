package com.yagay.ysuite.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.yui.YFilterBar
import com.yagay.yui.YTextField

data class YSuiteFilterOption(val id: String, val label: String)

/** Search and filter controls of YFiles/YDownload use the real YUI implementations. */
@Composable
fun YSuiteSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    YTextField(value = value, onValueChange = onValueChange, label = label, modifier = modifier, enabled = enabled)
}

@Composable
fun YSuiteFilterBar(
    options: List<YSuiteFilterOption>,
    selectedId: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    YFilterBar(
        options = options.map { it.label },
        selectedIndex = selectedIndex,
        onSelected = { index -> options.getOrNull(index)?.let { onSelected(it.id) } },
        modifier = modifier,
    )
}
