package com.yagay.ysuite.feature.ydownload

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ydownload.api.YDownloadBackend
import com.yagay.ysuite.productui.settings.ComposeSettingsChoice
import com.yagay.ysuite.productui.settings.ComposeSettingsChoiceGroup

@Composable
internal fun YDownloadBackendSettingsContent(
    backend: YDownloadBackend,
    onBackendChanged: (YDownloadBackend) -> Unit,
) {
    ComposeSettingsChoiceGroup(
        title =
            stringResource(
                R.string
                    .ydownload_default_backend,
            ),
        selectedId = backend.name,
        choices =
            listOf(
                ComposeSettingsChoice(
                    id =
                        YDownloadBackend.System
                            .name,
                    label =
                        stringResource(
                            R.string
                                .ydownload_backend_system,
                        ),
                ),
                ComposeSettingsChoice(
                    id =
                        YDownloadBackend.Private
                            .name,
                    label =
                        stringResource(
                            R.string
                                .ydownload_backend_private,
                        ),
                ),
            ),
        onSelected = { id ->
            runCatching {
                YDownloadBackend
                    .valueOf(id)
            }.getOrNull()
                ?.let(onBackendChanged)
        },
    )
}
