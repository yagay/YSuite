package com.yagay.YTaskManager

import android.content.Context
import com.yagay.YTaskManager.model.FrameworkState
import com.yagay.suite.api.XposedHostBridge
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real app-side LSPosed API-102 runtime shared by standalone YTaskManager and YSuite.
 * Files under /data/adb are not treated as proof that this module is connected or active.
 */
class YTaskManagerRuntime private constructor(context: Context) : XposedServiceHelper.OnServiceListener {
    private val appContext = context.applicationContext

    private val mutableFramework = MutableStateFlow(
        FrameworkState(detected = false, detail = "LSPosed API 102 service not connected")
    )
    val framework: StateFlow<FrameworkState> = mutableFramework.asStateFlow()

    @Volatile
    private var currentService: XposedService? = null

    @Volatile
    private var enabled: Boolean = true

    init {
        when (XposedHostBridge.attachListener(appContext, FEATURE_ID, this)) {
            XposedHostBridge.AttachResult.NOT_SUITE_HOST -> {
                XposedServiceHelper.registerListener(this)
                AppLogger.i("Standalone LSPosed runtime listener registered for ${appContext.packageName}")
            }
            XposedHostBridge.AttachResult.ATTACHED ->
                AppLogger.i("LSPosed runtime attached to managed host broker")
            XposedHostBridge.AttachResult.HOST_PRESENT_BUT_FAILED ->
                AppLogger.e("Managed host LSPosed broker attach failed")
        }
    }

    internal fun setManagedEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            currentService = null
            mutableFramework.value = FrameworkState(
                detected = false,
                detail = "YTaskManager feature disabled"
            )
            AppLogger.i("Managed runtime disabled; LSPosed service released")
        } else {
            AppLogger.i("Managed runtime enabled; waiting for LSPosed replay")
        }
    }

    override fun onServiceBind(service: XposedService) {
        if (!enabled) return
        currentService = service
        val api = runCatching { service.apiVersion }.getOrDefault(0)
        val name = runCatching { service.frameworkName }.getOrDefault("LSPosed")
        val version = runCatching { service.frameworkVersion }.getOrDefault("")
        val scopeCount = runCatching { service.scope?.size ?: 0 }.getOrDefault(0)
        val ready = api >= 102
        mutableFramework.value = FrameworkState(
            detected = ready,
            detail = if (ready) {
                buildString {
                    append(name)
                    if (version.isNotBlank()) append(' ').append(version)
                    append(" · API ").append(api)
                    append(" · Scope ").append(scopeCount)
                }
            } else {
                "Framework API $api detected; API 102 required"
            }
        )
        AppLogger.i("LSPosed service bound: ${mutableFramework.value.detail}")
    }

    override fun onServiceDied(service: XposedService) {
        if (currentService !== service) return
        currentService = null
        mutableFramework.value = FrameworkState(
            detected = false,
            detail = "LSPosed API 102 service disconnected"
        )
        AppLogger.i("LSPosed service disconnected")
    }

    companion object {
        private const val FEATURE_ID = "ytaskmanager"
        @Volatile private var instance: YTaskManagerRuntime? = null

        @JvmStatic
        fun get(context: Context): YTaskManagerRuntime {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: YTaskManagerRuntime(context.applicationContext).also { instance = it }
            }
        }
    }
}
