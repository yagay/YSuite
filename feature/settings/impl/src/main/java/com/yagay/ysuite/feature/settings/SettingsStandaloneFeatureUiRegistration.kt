package com.yagay.ysuite.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.settings.api.SettingsFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.settings.DataStoreAppSettingsRepository
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

object SettingsStandaloneFeatureUiRegistration : YSuiteFeatureUiRegistration {
    override val contract = SettingsFeatureContract
    override val productSurface = ProductSurfaceKind.Settings

    @Composable
    override fun label(): String =
        stringResource(R.string.settings_title)

    @Composable
    override fun Content() {
        val context = LocalContext.current.applicationContext
        val repository = remember(context) {
            DataStoreAppSettingsRepository(context)
        }
        SettingsFeatureScreen(repository = repository)
    }
}
