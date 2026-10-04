package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.navigation.FeatureRegistration

interface YSuiteFeatureUiRegistration {
    val contract: FeatureRegistration

    @Composable
    fun label(): String

    @Composable
    fun Content()
}
