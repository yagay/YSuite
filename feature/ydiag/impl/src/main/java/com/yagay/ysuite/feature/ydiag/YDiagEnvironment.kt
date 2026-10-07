package com.yagay.ysuite.feature.ydiag

import android.content.Context
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway

data class YDiagEnvironment(
    val applicationContext: Context,
    val rootGateway: RootGateway,
    val hookGateway: HookGateway,
    val logger: YSuiteLogger,
)

object YDiagEnvironmentFactory {
    fun create(
        context: Context,
        rootGateway: RootGateway,
        hookGateway: HookGateway,
        logger: YSuiteLogger,
    ): YDiagEnvironment =
        YDiagEnvironment(
            applicationContext = context.applicationContext,
            rootGateway = rootGateway,
            hookGateway = hookGateway,
            logger = logger,
        )
}
