package com.yagay.ysuite.feature.yfloat
import android.content.Context
import com.yagay.ysuite.platform.api.HookGateway
data class YFloatEnvironment(
    val applicationContext: Context,
    val hookGateway: HookGateway,
)
object YFloatEnvironmentFactory {
    fun create(context: Context, hookGateway: HookGateway) =
        YFloatEnvironment(context.applicationContext, hookGateway)
}
