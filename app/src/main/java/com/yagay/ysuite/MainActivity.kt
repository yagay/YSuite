package com.yagay.ysuite

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.yagay.ysuite.ui.YSuiteApplication

class MainActivity : AppCompatActivity() {
    private val container by lazy {
        YSuiteAppContainer(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YSuiteApplication(
                settingsRepository = container.settings,
                featureRegistry = container.featureRegistry,
            )
        }
    }
}
