package com.yagay.ysuite.feature.yparam

import android.content.Context
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway

data class YParamEnvironment(
    val applicationContext: Context,
    val hookGateway: HookGateway,
    val logger: YSuiteLogger,
)

object YParamEnvironmentFactory {
    fun create(
        context: Context,
        hookGateway: HookGateway,
        logger: YSuiteLogger,
    ): YParamEnvironment =
        YParamEnvironment(
            applicationContext = context.applicationContext,
            hookGateway = hookGateway,
            logger = logger,
        )
}
