package com.yagay.ysuite.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.yagay.yui.YTheme

/**
 * Compatibility entry point for rebuilt features. All appearance, dark mode,
 * dynamic color and system-bar logic is owned by YTheme, not by this adapter.
 */
@Composable
fun YSuiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColorEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    YTheme(dynamicColor = dynamicColorEnabled, darkTheme = darkTheme, content = content)
}
