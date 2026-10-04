package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.resources.R

@Composable
fun YSuiteApplication() {
    YSuiteRoot {
        YSuiteAppShell(title = stringResource(R.string.app_name)) { padding ->
            YSuiteArchitectureOverview(padding)
        }
    }
}
