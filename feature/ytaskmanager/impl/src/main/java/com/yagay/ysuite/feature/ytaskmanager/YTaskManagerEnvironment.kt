package com.yagay.ysuite.feature.ytaskmanager

import android.content.Context
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway

data class YTaskManagerEnvironment(
    val applicationContext: Context,
    val rootGateway: RootGateway,
    val hookGateway: HookGateway,
    val logger: YSuiteLogger,
)

object YTaskManagerEnvironmentFactory {
    fun create(
        context: Context,
        rootGateway: RootGateway,
        hookGateway: HookGateway,
        logger: YSuiteLogger,
    ): YTaskManagerEnvironment =
        YTaskManagerEnvironment(
            applicationContext = context.applicationContext,
            rootGateway = rootGateway,
            hookGateway = hookGateway,
            logger = logger,
        )
}
