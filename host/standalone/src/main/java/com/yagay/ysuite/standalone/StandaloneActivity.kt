package com.yagay.ysuite.standalone

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.yagay.ysuite.platform.android.DefaultPlatformServices
import com.yagay.ysuite.settings.DataStoreAppSettingsRepository
import com.yagay.ysuite.ui.YSuiteApplication
import com.yagay.ysuite.ui.YSuiteFeatureRegistry
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneAwareRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneDependencies

class StandaloneActivity : AppCompatActivity() {
    private val settings by lazy {
        DataStoreAppSettingsRepository(applicationContext)
    }

    private val featureRegistration by lazy {
        loadFeatureRegistration()
    }

    private val featureRegistry by lazy {
        YSuiteFeatureRegistry(
            listOf(featureRegistration),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YSuiteApplication(
                settingsRepository = settings,
                featureRegistry = featureRegistry,
                singleFeature = true,
            )
        }
    }

    private fun loadFeatureRegistration(): YSuiteFeatureUiRegistration {
        val registrationClass = Class.forName(
            BuildConfig.FEATURE_REGISTRATION_CLASS,
        )
        val instance = registrationClass.getField("INSTANCE").get(null)

        val registration =
            instance as? YSuiteFeatureUiRegistration
                ?: error(
                "Standalone registration does not implement " +
                    "YSuiteFeatureUiRegistration: " +
                    BuildConfig.FEATURE_REGISTRATION_CLASS,
                )

        val platform =
            DefaultPlatformServices.create()

        (registration as? YSuiteStandaloneAwareRegistration)
            ?.bindStandaloneDependencies(
                YSuiteStandaloneDependencies(
                    rootGateway = platform.root,
                    shizukuGateway =
                        platform.shizuku,
                    hookGateway =
                        platform.hooks,
                ),
            )

        return registration
    }
}
