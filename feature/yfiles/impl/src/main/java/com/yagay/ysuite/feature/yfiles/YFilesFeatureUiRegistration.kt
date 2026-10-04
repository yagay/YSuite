package com.yagay.ysuite.feature.yfiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yfiles.api.YFilesFeatureContract
import com.yagay.ysuite.feature.yfiles.api.YFilesPlacesRepository
import com.yagay.ysuite.feature.yfiles.api.YFilesRepository
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.runtime.FeatureLifecycleEvent
import com.yagay.ysuite.runtime.FeatureLifecycleObserver
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YFilesFeatureUiRegistration(
    private val repository: YFilesRepository,
    private val placesRepository: YFilesPlacesRepository,
    private val logger: YSuiteLogger,
) : YSuiteFeatureUiRegistration {
    override val contract = YFilesFeatureContract

    override val lifecycleObserver =
        FeatureLifecycleObserver { event ->
            when (event) {
                FeatureLifecycleEvent.Activated ->
                    logger.debug(TAG, "YFiles activated")
                FeatureLifecycleEvent.Deactivated ->
                    logger.debug(TAG, "YFiles deactivated")
            }
        }

    @Composable
    override fun label(): String =
        stringResource(R.string.yfiles_title)

    @Composable
    override fun Content() {
        YFilesFeatureScreen(
            repository = repository,
            placesRepository = placesRepository,
            logger = logger,
        )
    }

    companion object {
        private const val TAG = "YSuite/YFiles"
    }
}
