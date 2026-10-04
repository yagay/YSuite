package com.yagay.ysuite.feature.ydownload

import android.content.Context
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.YSuiteLogger
import okhttp3.OkHttpClient

data class YDownloadEnvironment(
    val repository: YDownloadRepository,
    val settings: YDownloadSettingsRepository,
    val engine: YDownloadEngine,
    val metadataFetcher: YDownloadMetadataFetcher,
    val scheduler: YDownloadScheduler,
)

object YDownloadEnvironmentFactory {
    fun create(
        context: Context,
        logger: YSuiteLogger,
    ): YDownloadEnvironment =
        YDownloadRuntime.obtain(
            context = context.applicationContext,
            logger = logger,
        )
}

internal object YDownloadRuntime {
    @Volatile
    private var environment: YDownloadEnvironment? = null

    private val loggerBridge = RuntimeLoggerBridge()

    fun obtain(
        context: Context,
        logger: YSuiteLogger? = null,
    ): YDownloadEnvironment {
        logger?.let(loggerBridge::bind)

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
                ).also {
                    environment = it
                }
            }
        }
    }

    private class RuntimeLoggerBridge : YSuiteLogger {
        @Volatile
        private var delegate: YSuiteLogger? = null

        fun bind(logger: YSuiteLogger) {
            delegate = logger
        }

        override fun log(record: LogRecord) {
            delegate?.log(record)
        }
    }
}
