package com.yagay.YEntryCleaner.xposed

import com.yagay.YEntryCleaner.domain.BrowserLinkConfig
import com.yagay.YEntryCleaner.domain.DisplayMode
import com.yagay.YEntryCleaner.domain.IntentKind
import com.yagay.YEntryCleaner.domain.OpenTypeConfig
import com.yagay.YEntryCleaner.domain.PriorityConfig
import com.yagay.YEntryCleaner.domain.VisibilityCompatConfig
import com.yagay.YEntryCleaner.domain.selectedKinds

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
    val allSelectedPackages: Set<String> = visibilityCompat.activePackages()

    fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds
}
