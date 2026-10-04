package com.yagay.ysuite

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.yagay.ysuite.ui.YSUITE_EXTRA_INITIAL_FEATURE_ID
import com.yagay.ysuite.ui.YSuiteApplication

class MainActivity : AppCompatActivity() {
    private var requestedFeatureId by
        mutableStateOf<String?>(null)

    private val container by lazy {
        YSuiteAppContainer(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedFeatureId =
            intent.getStringExtra(
                YSUITE_EXTRA_INITIAL_FEATURE_ID,
            )
        enableEdgeToEdge()
        setContent {
            YSuiteApplication(
                settingsRepository = container.settings,
                featureRegistry = container.featureRegistry,
                initialFeatureId = requestedFeatureId,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedFeatureId =
            intent.getStringExtra(
                YSUITE_EXTRA_INITIAL_FEATURE_ID,
            )
    }
}
