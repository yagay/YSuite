package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.runtime.FeatureLifecycleObserver

interface YSuiteFeatureUiRegistration {
    val contract: FeatureRegistration

    val lifecycleObserver: FeatureLifecycleObserver
        get() = FeatureLifecycleObserver.None

    /**
     * Declares the primary UI surface for this feature.
     * The host uses this metadata for consistent navigation and diagnostics,
     * while the feature remains free to choose the matching adaptive shell.
     */
    val pageKind: YSuitePageKind
        get() = YSuitePageKind.Tool

    @Composable
    fun label(): String

    @Composable
    fun Content()
}
