package com.yagay.ydownload

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yagay.yui.YComposeActivity
import com.yagay.ysuite.feature.ydownload.YDownloadEnvironmentFactory
import com.yagay.ysuite.feature.ydownload.YDownloadFeatureScreen
import com.yagay.ysuite.logging.api.LogLevel
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.android.DefaultPlatformServices

/** YUI host bridge only. Repository, transfer engine and pages come from the rebuilt module. */
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
                        "YDownload/" + record.tag,
                        record.message,
                    )
                }
            }
        }
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
