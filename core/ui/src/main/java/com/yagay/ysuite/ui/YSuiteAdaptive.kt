package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens

enum class YSuiteWidthClass {
    Compact,
    Medium,
    Expanded,
}

@Composable
fun YSuiteAdaptiveContainer(
    modifier: Modifier = Modifier,
    content: @Composable (YSuiteWidthClass) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthClass = when {
            maxWidth <= YSuiteLayoutTokens.CompactMaxWidth -> YSuiteWidthClass.Compact
            maxWidth <= YSuiteLayoutTokens.MediumMaxWidth -> YSuiteWidthClass.Medium
            else -> YSuiteWidthClass.Expanded
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = YSuiteLayoutTokens.ContentMaxWidth)
                .align(Alignment.TopCenter),
        ) {
            content(widthClass)
        }
    }
}
