package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.runtime.FeatureLifecycleObserver

interface YSuiteFeatureUiRegistration {
    val contract: FeatureRegistration

    /**
     * Required product-level UI contract.
     *
     * There is intentionally no default. Every feature must make an explicit
     * product decision instead of silently falling back to a generic page.
     */
    val productSurface: ProductSurfaceKind

    val lifecycleObserver: FeatureLifecycleObserver
        get() = FeatureLifecycleObserver.None

    @Composable
    fun label(): String

    @Composable
    fun Content()
}
