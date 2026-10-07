package com.yagay.ysuite.feature.ypower

import android.content.Context
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway

data class YPowerEnvironment(
    val applicationContext: Context,
    val rootGateway: RootGateway,
    val hookGateway: HookGateway,
    val logger: YSuiteLogger,
)

object YPowerEnvironmentFactory {
    fun create(
        context: Context,
        rootGateway: RootGateway,
        hookGateway: HookGateway,
        logger: YSuiteLogger,
    ): YPowerEnvironment =
        YPowerEnvironment(
            applicationContext = context.applicationContext,
            rootGateway = rootGateway,
            hookGateway = hookGateway,
            logger = logger,
        )
}
