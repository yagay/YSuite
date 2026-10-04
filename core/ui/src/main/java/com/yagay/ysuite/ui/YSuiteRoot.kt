package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.designsystem.theme.YSuiteTheme

@Composable
fun YSuiteRoot(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    if (darkTheme == null) {
        YSuiteTheme(content = content)
    } else {
        YSuiteTheme(darkTheme = darkTheme, content = content)
    }
}
