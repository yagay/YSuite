package com.yagay.ysuite.feature.yminiguard

import android.content.Context
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway

data class YMiniGuardEnvironment(
    val applicationContext: Context,
    val rootGateway: RootGateway,
    val hookGateway: HookGateway,
    val logger: YSuiteLogger,
)

object YMiniGuardEnvironmentFactory {
    fun create(
        context: Context,
        rootGateway: RootGateway,
        hookGateway: HookGateway,
        logger: YSuiteLogger,
    ): YMiniGuardEnvironment =
        YMiniGuardEnvironment(
            context.applicationContext,
            rootGateway,
            hookGateway,
            logger,
        )
}
