package com.yagay.yfiles

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yagay.yui.YComposeActivity
import com.yagay.ysuite.feature.yfiles.YFilesEnvironmentFactory
import com.yagay.ysuite.feature.yfiles.YFilesFeatureScreen
import com.yagay.ysuite.logging.api.LogLevel
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.android.DefaultPlatformServices

/** YUI host bridge only. Browser, providers and tools live entirely in the rebuilt feature. */
class MainActivity : YComposeActivity() {
    private val platform by lazy(LazyThreadSafetyMode.NONE) { DefaultPlatformServices.create() }

    @Composable
    override fun YContent() {
        val logger = remember {
            object : YSuiteLogger {
                override fun log(record: LogRecord) {
                    Log.println(
                        when (record.level) {
                            LogLevel.Verbose -> Log.VERBOSE
                            LogLevel.Debug -> Log.DEBUG
                            LogLevel.Info -> Log.INFO
                            LogLevel.Warning -> Log.WARN
                            LogLevel.Error -> Log.ERROR
                        },
                        "YFiles/" + record.tag,
                        record.message,
                    )
                }
            }
        }
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
