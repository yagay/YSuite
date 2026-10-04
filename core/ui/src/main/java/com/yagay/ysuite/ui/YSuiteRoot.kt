package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.designsystem.theme.YSuiteTheme

@Composable
fun YSuiteRoot(content: @Composable () -> Unit) {
    YSuiteTheme(content = content)
}
