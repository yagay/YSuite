package com.yagay.ysuite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.resources.R
import com.yagay.ysuite.ui.YSuiteAppShell
import com.yagay.ysuite.ui.YSuiteArchitectureOverview
import com.yagay.ysuite.ui.YSuiteRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YSuiteRoot {
                YSuiteAppShell(title = stringResource(R.string.app_name)) { padding ->
                    YSuiteArchitectureOverview(padding)
                }
            }
        }
    }
}
