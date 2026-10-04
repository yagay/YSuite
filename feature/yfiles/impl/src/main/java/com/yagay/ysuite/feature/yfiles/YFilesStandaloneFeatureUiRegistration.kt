package com.yagay.ysuite.feature.yfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yfiles.api.YFilesFeatureContract
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

object YFilesStandaloneFeatureUiRegistration :
    YSuiteFeatureUiRegistration {
    override val contract = YFilesFeatureContract

    private val logger: YSuiteLogger =
        CompositeYSuiteLogger(
            listOf(InMemoryLogStore()),
        )

    @Composable
    override fun label(): String =
        stringResource(R.string.yfiles_title)

    @Composable
    override fun Content() {
        val context = LocalContext.current.applicationContext
        val repository = remember {
            LocalYFilesRepository()
        }
        val placesRepository = remember(context) {
            LocalYFilesPlacesRepository(context)
        }

        YFilesFeatureScreen(
            repository = repository,
            placesRepository = placesRepository,
            logger = logger,
        )
    }
}
