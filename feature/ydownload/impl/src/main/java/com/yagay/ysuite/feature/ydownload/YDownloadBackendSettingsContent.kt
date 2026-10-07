package com.yagay.ysuite.feature.ydownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ydownload.api.YDownloadBackend
import com.yagay.ysuite.productui.settings.ComposeSettingsChoice
import com.yagay.ysuite.productui.settings.ComposeSettingsChoiceGroup

@Composable
internal fun YDownloadBackendSettingsContent() {
    val context =
        LocalContext.current
    val store =
        remember(context) {
            YDownloadBackendPreferenceStore(
                context,
            )
        }
    var backend by
        remember {
            mutableStateOf(
                store.get(),
            )
        }

    ComposeSettingsChoiceGroup(
        title =
            stringResource(
                R.string
                    .ydownload_default_backend,
            ),
        subtitle =
            stringResource(
                R.string
                    .ydownload_default_backend_desc,
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
                ?.let {
                    backend = it
                    store.set(it)
                }
        },
    )
}
