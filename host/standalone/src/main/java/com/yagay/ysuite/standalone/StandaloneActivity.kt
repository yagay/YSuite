package com.yagay.ysuite.standalone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yagay.ysuite.feature.template.TemplateFeatureScreen
import com.yagay.ysuite.ui.YSuiteRoot

class StandaloneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YSuiteRoot {
                TemplateFeatureScreen()
            }
        }
    }
}
