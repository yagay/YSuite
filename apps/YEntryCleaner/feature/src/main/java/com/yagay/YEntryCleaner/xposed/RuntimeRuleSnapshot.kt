package com.yagay.YEntryCleaner.xposed

import com.yagay.YEntryCleaner.BuildConfig
import com.yagay.YEntryCleaner.domain.BrowserLinkConfig
import com.yagay.YEntryCleaner.domain.DisplayMode
import com.yagay.YEntryCleaner.domain.IntentKind
import com.yagay.YEntryCleaner.domain.OpenTypeConfig
import com.yagay.YEntryCleaner.domain.PriorityConfig
import com.yagay.YEntryCleaner.domain.VisibilityCompatConfig
import com.yagay.YEntryCleaner.domain.selectedKinds
import com.yagay.suite.api.RuntimeOwnerGate

internal data class RuntimeRuleSnapshot(
    val configured: Set<String>,
    val displayMode: DisplayMode,
    val priorities: PriorityConfig,
    val openTypes: OpenTypeConfig,
    val browserLinks: BrowserLinkConfig,
    val diagnostic: Boolean,
    val managerAppId: Int = -1,
    val digest: String = "",
    val hiddenFromApps: Set<String> = emptySet(),
    val visibilityCompat: VisibilityCompatConfig = VisibilityCompatConfig(),
) {
    private val selectedKinds: Set<IntentKind> = selectedKinds(configured)

    private fun ownerActive(): Boolean =
        RuntimeOwnerGate.shouldRun("yentrycleaner", BuildConfig.HOST_PACKAGE)

    /** Emptying selected targets makes already-installed standalone resolver hooks pass-through. */
    val allSelectedPackages: Set<String>
        get() = if (ownerActive()) visibilityCompat.activePackages() else emptySet()

    fun hasSelection(kind: IntentKind): Boolean = ownerActive() && kind in selectedKinds
}
