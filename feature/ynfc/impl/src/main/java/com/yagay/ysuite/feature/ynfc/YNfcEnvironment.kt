package com.yagay.ysuite.feature.ynfc
import android.content.Context
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway
data class YNfcEnvironment(
    val applicationContext: Context,
    val rootGateway: RootGateway,
    val hookGateway: HookGateway,
    val logger: YSuiteLogger,
)
object YNfcEnvironmentFactory {
    fun create(context: Context, rootGateway: RootGateway, hookGateway: HookGateway, logger: YSuiteLogger) =
        YNfcEnvironment(context.applicationContext, rootGateway, hookGateway, logger)
}
