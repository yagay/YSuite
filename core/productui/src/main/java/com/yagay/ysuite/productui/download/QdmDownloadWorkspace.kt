package com.yagay.ysuite.productui.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo

data class QdmDownloadTab(
    val id: String,
    val label: String,
)

/**
 * Download workspace aligned with the Apache-2.0 QDM-Android main screen:
 * app bar -> search/filter -> state tabs -> download collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QdmDownloadWorkspace(
    title: String,
    tabs: List<QdmDownloadTab>,
    selectedTabId: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    searchAndFilters: @Composable () -> Unit = {},
    content: @Composable (ProductAdaptiveInfo, String) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon,
            actions = actions,
        )
        searchAndFilters()
        if (tabs.isNotEmpty()) {
            val selectedIndex =
                tabs.indexOfFirst { it.id == selectedTabId }
                    .coerceAtLeast(0)
            PrimaryTabRow(selectedTabIndex = selectedIndex) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab.id == selectedTabId,
                        onClick = { onTabSelected(tab.id) },
                        text = { Text(tab.label) },
                    )
                }
            }
        }
        ProductAdaptiveBox(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) { adaptive ->
            content(adaptive, selectedTabId)
        }
    }
}
