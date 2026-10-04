package com.yagay.ysuite.standalone

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.yagay.ysuite.feature.template.TemplateFeatureScreen
import com.yagay.ysuite.ui.YSuiteRoot

class StandaloneActivity : AppCompatActivity() {
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
