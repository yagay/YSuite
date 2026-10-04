package com.yagay.ysuite.standalone

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.yagay.ysuite.settings.DataStoreAppSettingsRepository
import com.yagay.ysuite.standalone.generated.standaloneFeatureRegistration
import com.yagay.ysuite.ui.YSuiteApplication
import com.yagay.ysuite.ui.YSuiteFeatureRegistry

class StandaloneActivity : AppCompatActivity() {
    private val settings by lazy {
        DataStoreAppSettingsRepository(applicationContext)
    }

    private val featureRegistry by lazy {
        YSuiteFeatureRegistry(
            listOf(standaloneFeatureRegistration),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YSuiteApplication(
                settingsRepository = settings,
                featureRegistry = featureRegistry,
            )
        }
    }
}
