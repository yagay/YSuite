package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.designsystem.theme.YSuiteTheme

@Composable
fun YSuiteRoot(
    darkTheme: Boolean? = null,
    dynamicColorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (darkTheme == null) {
        YSuiteTheme(
            dynamicColorEnabled =
                dynamicColorEnabled,
            content = content,
        )
    } else {
        YSuiteTheme(
            darkTheme = darkTheme,
            dynamicColorEnabled =
                dynamicColorEnabled,
            content = content,
        )
    }
}
