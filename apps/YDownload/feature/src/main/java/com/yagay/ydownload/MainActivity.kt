package com.yagay.ydownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yagay.yui.YComposeActivity
import com.yagay.ysuite.feature.ydownload.YDownloadEnvironmentFactory
import com.yagay.ysuite.feature.ydownload.YDownloadFeatureScreen
import com.yagay.ysuite.platform.android.DefaultPlatformServices
import com.yagay.ysuite.logging.android.FeatureAndroidLogger

/** YUI host bridge only. Repository, transfer engine and pages come from the rebuilt module. */
class MainActivity : YComposeActivity() {
    private val platform by lazy(LazyThreadSafetyMode.NONE) { DefaultPlatformServices.create() }

    @Composable
    override fun YContent() {
        val logger = remember { FeatureAndroidLogger("YDownload") }
        val environment = remember {
            YDownloadEnvironmentFactory.create(
                context = applicationContext,
                logger = logger,
                hookGateway = platform.hooks,
            )
        }
        YDownloadFeatureScreen(environment = environment, logger = logger)
    }
}
