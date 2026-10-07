package com.yagay.ysuite.feature.ydownload

import android.content.Context
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
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
            hookGateway = hookBridge,
        )
}

internal object YDownloadRuntime {
    @Volatile
    private var environment: YDownloadEnvironment? = null

    private val loggerBridge = RuntimeLoggerBridge()
    private val hookBridge = RuntimeHookGatewayBridge()

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

    private class RuntimeHookGatewayBridge :
        HookGateway {
        @Volatile
        private var delegate: HookGateway? = null

        fun bind(gateway: HookGateway) {
            delegate = gateway
        }

        override suspend fun status():
            CapabilityStatus =
            delegate?.status()
                ?: CapabilityStatus.Unavailable

        override suspend fun reload(
            scopePackages: Set<String>,
        ): Outcome<Unit> =
            delegate?.reload(scopePackages)
                ?: Outcome.Failure(
                    code = "hook_unavailable",
                    message =
                        HOOK_UNAVAILABLE_MESSAGE,
                    retryable = true,
                )

        override suspend fun writeConfig(
            group: String,
            key: String,
            value: String?,
        ): Outcome<Unit> =
            delegate?.writeConfig(
                group = group,
                key = key,
                value = value,
            ) ?: Outcome.Failure(
                code = "hook_unavailable",
                message =
                    HOOK_UNAVAILABLE_MESSAGE,
                retryable = true,
            )
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

private const val HOOK_UNAVAILABLE_MESSAGE =
    "Hook service is unavailable"
