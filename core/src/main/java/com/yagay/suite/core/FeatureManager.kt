package com.yagay.suite.core

import android.content.Context
import android.content.Intent

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

    fun initialize(context: Context) {
        val className = runtimeInitializerClassName ?: return
        val runtimeClass = Class.forName(className)
        runtimeClass.getMethod("get", Context::class.java).invoke(null, context.applicationContext)
    }
}

object FeatureRegistry {
    val all: List<FeatureSpec> = listOf(
        FeatureSpec(
            id = "listcleaner",
            name = "ListCleaner",
            description = "分享、打开方式与组件列表清理",
            entryActivityClassName = "com.yagay.ListCleaner.ui.MainActivity",
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
    )

    fun included(): List<FeatureSpec> = all.filter(FeatureSpec::isIncluded)
}

class FeatureStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ysuite_features", Context.MODE_PRIVATE)

    fun isEnabled(feature: FeatureSpec): Boolean = prefs.getBoolean("enabled.${feature.id}", feature.defaultEnabled)

    fun setEnabled(feature: FeatureSpec, enabled: Boolean) {
        prefs.edit().putBoolean("enabled.${feature.id}", enabled).apply()
    }

    fun isVisible(feature: FeatureSpec): Boolean = prefs.getBoolean("visible.${feature.id}", true)

    fun setVisible(feature: FeatureSpec, visible: Boolean) {
        prefs.edit().putBoolean("visible.${feature.id}", visible).apply()
    }
}
