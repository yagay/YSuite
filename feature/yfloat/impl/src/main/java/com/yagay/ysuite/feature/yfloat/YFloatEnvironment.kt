package com.yagay.ysuite.feature.yfloat

import android.content.Context
import com.yagay.YFloat.compat.YFloatRuntimeHost
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway

data class YFloatEnvironment(
    val applicationContext: Context,
    val rootGateway: RootGateway,
    val hookGateway: HookGateway,
    val logger: YSuiteLogger,
)

object YFloatEnvironmentFactory {
    fun create(
        context: Context,
        rootGateway: RootGateway,
        hookGateway: HookGateway,
        logger: YSuiteLogger,
    ): YFloatEnvironment {
        YFloatRuntimeHost.install(rootGateway, logger)
        return YFloatEnvironment(
            context.applicationContext,
            rootGateway,
            hookGateway,
            logger,
        )
    }
}
