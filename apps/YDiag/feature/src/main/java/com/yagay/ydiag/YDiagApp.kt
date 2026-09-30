package com.yagay.ydiag

import android.app.Application
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin standalone APK shell. The real runtime is host-neutral so YSuite can use
 * exactly the same feature code without pretending to be a YDiag Application.
 */
class YDiagApp : Application() {
    private val runtime: YDiagRuntime
        get() = YDiagRuntime.get(this)

    val moduleState: StateFlow<ModuleState>
        get() = runtime.moduleState

    override fun onCreate() {
        super.onCreate()
        runtime
    }

    fun syncDeepTracking(targets: Set<String>, options: Set<String>) =
        runtime.syncDeepTracking(targets, options)

    fun refreshModuleState() = runtime.refreshModuleState()

    companion object {
        const val PREFS = YDiagRuntime.PREFS
        const val KEY_TARGETS = YDiagRuntime.KEY_TARGETS
        const val KEY_OPTIONS = YDiagRuntime.KEY_OPTIONS
        const val KEY_REVISION = YDiagRuntime.KEY_REVISION
        val DEFAULT_SCOPE: Set<String> = YDiagRuntime.DEFAULT_SCOPE
    }
}
