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

enum class YSuiteHeightClass {
    Compact,
    Medium,
    Expanded,
}

data class YSuiteAdaptiveInfo(
    val widthClass: YSuiteWidthClass,
    val heightClass: YSuiteHeightClass,
) {
    val isCompact: Boolean
        get() = widthClass == YSuiteWidthClass.Compact

    val isWide: Boolean
        get() = widthClass == YSuiteWidthClass.Expanded

    val isShort: Boolean
        get() = heightClass == YSuiteHeightClass.Compact
}

@Composable
fun YSuiteAdaptiveLayout(
    modifier: Modifier = Modifier,
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val widthClass = when {
            maxWidth <= YSuiteLayoutTokens.CompactMaxWidth ->
                YSuiteWidthClass.Compact
            maxWidth <= YSuiteLayoutTokens.MediumMaxWidth ->
                YSuiteWidthClass.Medium
            else ->
                YSuiteWidthClass.Expanded
        }
        val heightClass = when {
            maxHeight <= YSuiteLayoutTokens.CompactMaxHeight ->
                YSuiteHeightClass.Compact
            maxHeight <= YSuiteLayoutTokens.MediumMaxHeight ->
                YSuiteHeightClass.Medium
            else ->
                YSuiteHeightClass.Expanded
        }

        content(
            YSuiteAdaptiveInfo(
                widthClass = widthClass,
                heightClass = heightClass,
            ),
        )
    }
}

/**
 * Compatibility wrapper for content that only needs a width class.
 * New page shells should use [YSuiteAdaptiveLayout] so full-width surfaces
 * such as browsers and managers are not forced into a document width.
 */
@Composable
fun YSuiteAdaptiveContainer(
    modifier: Modifier = Modifier,
    content: @Composable (YSuiteWidthClass) -> Unit,
) {
    YSuiteAdaptiveLayout(modifier = modifier.fillMaxWidth()) { info ->
        Box(
            modifier = Modifier
                .widthIn(max = YSuiteLayoutTokens.ContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            content(info.widthClass)
        }
    }
}
