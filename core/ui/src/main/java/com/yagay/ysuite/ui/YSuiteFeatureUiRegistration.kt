package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.runtime.FeatureLifecycleObserver

interface YSuiteFeatureUiRegistration {
    val contract: FeatureRegistration

    val lifecycleObserver: FeatureLifecycleObserver
        get() = FeatureLifecycleObserver.None

    @Composable
    fun label(): String

    @Composable
    fun Content()
}
