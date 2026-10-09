package com.yagay.ysuite.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.yui.YStatusPill
import com.yagay.yui.YStatusTone

enum class YSuiteStatusTone { Positive, Warning, Error, Neutral }

/** Feature-specific tone enum retained for source compatibility; YUI owns badge rendering. */
@Composable
fun YSuiteStatusBadge(text: String, tone: YSuiteStatusTone, modifier: Modifier = Modifier) {
    val yuiTone = when (tone) {
        YSuiteStatusTone.Positive -> YStatusTone.Good
        YSuiteStatusTone.Warning -> YStatusTone.Warning
        YSuiteStatusTone.Error -> YStatusTone.Error
        YSuiteStatusTone.Neutral -> YStatusTone.Neutral
    }
    YStatusPill(label = "", value = text, tone = yuiTone, modifier = modifier)
}
