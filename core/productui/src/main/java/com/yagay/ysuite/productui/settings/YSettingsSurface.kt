package com.yagay.ysuite.productui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YSettingsSurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    categoryPane: @Composable (ProductAdaptiveInfo) -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    ProductAdaptiveBox(modifier = modifier.fillMaxSize()) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded) {
                Surface(
                    modifier = Modifier
                        .width(256.dp)
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
                        .padding(YSuiteSpacing.Large),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content(adaptive)
                    }
                }
            }
        }
    }
}
