package com.yagay.ysuite.feature.ydownload

import android.content.Context
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.logging.api.BindableYSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.BindableHookGateway
import okhttp3.OkHttpClient

data class YDownloadEnvironment(
    val repository: YDownloadRepository,
    val settings: YDownloadSettingsRepository,
    val engine: YDownloadEngine,
    val metadataFetcher: YDownloadMetadataFetcher,
    val scheduler: YDownloadScheduler,
    val hookGateway: HookGateway,
)

object YDownloadEnvironmentFactory {
    fun create(
        context: Context,
        logger: YSuiteLogger,
        hookGateway: HookGateway,
    ): YDownloadEnvironment =
        YDownloadRuntime.obtain(
            context = context.applicationContext,
            logger = logger,
            hookGateway = hookGateway,
        )
}

internal object YDownloadRuntime {
    @Volatile
    private var environment: YDownloadEnvironment? = null

    private val loggerBridge = BindableYSuiteLogger()
    private val hookBridge = BindableHookGateway()

    fun obtain(
        context: Context,
        logger: YSuiteLogger? = null,
        hookGateway: HookGateway? = null,
    ): YDownloadEnvironment {
        logger?.let(loggerBridge::bind)
        hookGateway?.let(hookBridge::bind)

        environment?.let { return it }

        return synchronized(this) {
            environment ?: run {
                val client =
                    OkHttpClient.Builder()
                        .retryOnConnectionFailure(true)
                        .build()
                val repository =
                    YDownloadRepository(context)
                val settings =
                    YDownloadSettingsRepository(context)
                val engine =
                    YDownloadEngine(
                        context = context,
                        repository = repository,
                        settings = settings,
                        client = client,
                        logger = loggerBridge,
                        hookGateway = hookBridge,
                    )
                val scheduler =
                    YDownloadScheduler(context)
                YDownloadEnvironment(
                    repository = repository,
                    settings = settings,
                    engine = engine,
                    metadataFetcher =
                        YDownloadMetadataFetcher(client),
                    scheduler = scheduler,
                    hookGateway = hookBridge,
                ).also {
                    environment = it
                }
            }
        }
    }

}
