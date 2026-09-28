package com.yagay.suite.core

import android.content.Context
import android.content.Intent

/**
 * Minimal registration contract for one reusable feature.
 *
 * Keep feature-specific business logic out of this class. A feature must remain independently
 * buildable while exposing the same entry/runtime implementation to YSuite.
 */
data class FeatureSpec(
    val id: String,
    val name: String,
    val description: String,
    val entryActivityClassName: String,
    val runtimeInitializerClassName: String? = null,
    val requiresRoot: Boolean = false,
    val requiresHook: Boolean = false,
    val defaultEnabled: Boolean = true,
) {
    fun isIncluded(): Boolean = runCatching { Class.forName(entryActivityClassName) }.isSuccess

    fun createIntent(context: Context): Intent = Intent(context, Class.forName(entryActivityClassName))

    /** Returns the feature runtime so YSuite can attach shared process-level services to it. */
    fun initialize(context: Context): Any? {
        val className = runtimeInitializerClassName ?: return null
        val runtimeClass = Class.forName(className)
        return runtimeClass.getMethod("get", Context::class.java).invoke(null, context.applicationContext)
    }
}

object FeatureRegistry {
    val all: List<FeatureSpec> = listOf(
        FeatureSpec(
            id = "yentrycleaner",
            name = "YEntryCleaner",
            description = "分享、打开方式与组件入口清理",
            entryActivityClassName = "com.yagay.YEntryCleaner.ui.MainActivity",
            runtimeInitializerClassName = "com.yagay.YEntryCleaner.YEntryCleanerRuntime",
            requiresRoot = true,
            requiresHook = true,
        ),
        FeatureSpec(
            id = "ydiag",
            name = "YDiag",
            description = "应用日志与故障诊断",
            entryActivityClassName = "com.yagay.ydiag.ui.MainActivity",
            runtimeInitializerClassName = "com.yagay.ydiag.YDiagRuntime",
            requiresRoot = true,
            requiresHook = true,
        ),
        FeatureSpec(
            id = "ynotify",
            name = "YNotify",
            description = "通知 / Toast / 横幅历史",
            entryActivityClassName = "com.yagay.YNotify.ui.MainActivity",
            runtimeInitializerClassName = "com.yagay.YNotify.YNotifyRuntime",
            requiresHook = true,
        ),
        FeatureSpec(
            id = "ypower",
            name = "YPower",
            description = "应用增强、检测与运行时诊断",
            entryActivityClassName = "com.yagay.ypower.ui.MainActivity",
            runtimeInitializerClassName = "com.yagay.ypower.YPowerRuntime",
            requiresRoot = true,
            requiresHook = true,
        ),
        FeatureSpec(
            id = "yminiguard",
            name = "YMiniGuard",
            description = "小窗保活与后台播放守护",
            entryActivityClassName = "com.yagay.YMiniGuard.MainActivity",
            runtimeInitializerClassName = "com.yagay.YMiniGuard.GuardRuntime",
            requiresRoot = true,
            requiresHook = true,
        ),
        FeatureSpec(
            id = "ynfc",
            name = "YNFC",
            description = "NFC 门禁卡与控制器模拟",
            entryActivityClassName = "com.yagay.YNFC.MainActivity",
            runtimeInitializerClassName = "com.yagay.YNFC.YNFCSuiteRuntime",
            requiresRoot = true,
            requiresHook = true,
        ),
        FeatureSpec(
            id = "ytaskmanager",
            name = "YTaskManager",
            description = "进程、性能与系统任务管理",
            entryActivityClassName = "com.yagay.YTaskManager.MainActivity",
            runtimeInitializerClassName = "com.yagay.YTaskManager.YTaskManagerSuiteRuntime",
            requiresRoot = true,
            requiresHook = true,
        ),
        FeatureSpec(
            id = "yparam",
            name = "YParam",
            description = "应用 DPI、语言、定位与参数覆盖",
            entryActivityClassName = "com.yagay.yparam.ui.MainActivity",
            runtimeInitializerClassName = "com.yagay.yparam.YParamSuiteRuntime",
            requiresHook = true,
        ),
        FeatureSpec(
            id = "yfloat",
            name = "YFloat",
            description = "悬浮操作、文字选框与快捷动作",
            entryActivityClassName = "com.yagay.YFloat.MainActivity",
            runtimeInitializerClassName = "com.yagay.YFloat.YFloatSuiteRuntime",
            requiresRoot = true,
            requiresHook = true,
        ),
    )

    fun included(): List<FeatureSpec> = all.filter(FeatureSpec::isIncluded)
}

class FeatureStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        SuiteContract.FEATURE_STATE_PREFS,
        Context.MODE_PRIVATE,
    )

    fun isEnabled(feature: FeatureSpec): Boolean = prefs.getBoolean("enabled.${feature.id}", feature.defaultEnabled)

    fun setEnabled(feature: FeatureSpec, enabled: Boolean) {
        prefs.edit().putBoolean("enabled.${feature.id}", enabled).apply()
    }
}
