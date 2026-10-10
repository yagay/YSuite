package com.yagay.yfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yagay.yui.YComposeActivity
import com.yagay.ysuite.feature.yfiles.YFilesEnvironmentFactory
import com.yagay.ysuite.feature.yfiles.YFilesFeatureScreen
import com.yagay.ysuite.platform.android.DefaultPlatformServices
import com.yagay.ysuite.logging.android.FeatureAndroidLogger

/** YUI host bridge only. Browser, providers and tools live entirely in the rebuilt feature. */
class MainActivity : YComposeActivity() {
    private val platform by lazy(LazyThreadSafetyMode.NONE) { DefaultPlatformServices.create("yfiles") }

    @Composable
    override fun YContent() {
        val logger = remember { FeatureAndroidLogger("YFiles") }
        val environment = remember {
            YFilesEnvironmentFactory.create(
                context = applicationContext,
                rootGateway = platform.root,
                shizukuGateway = platform.shizuku,
                hookGateway = platform.hooks,
            )
        }
        YFilesFeatureScreen(environment = environment, logger = logger)
    }
}
