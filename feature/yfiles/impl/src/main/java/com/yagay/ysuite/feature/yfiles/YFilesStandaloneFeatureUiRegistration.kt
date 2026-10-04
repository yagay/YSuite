package com.yagay.ysuite.feature.yfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yfiles.api.YFilesFeatureContract
import com.yagay.ysuite.feature.yfiles.engine.DefaultYFilesEngine
import com.yagay.ysuite.feature.yfiles.engine.YFileProviderRegistry
import com.yagay.ysuite.feature.yfiles.provider.local.LocalFileProvider
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
        val engine = remember {
            DefaultYFilesEngine(
                YFileProviderRegistry(
                    listOf(
                        LocalFileProvider(),
                    ),
                ),
            )
        }

        YFilesFeatureScreen(
            engine = engine,
            logger = logger,
        )
    }
}
