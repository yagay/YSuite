package com.yagay.ysuite.feature.yfiles

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteFilterBar
import com.yagay.ysuite.designsystem.component.YSuiteFilterOption
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSearchField
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteSwitchItem
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.ui.YSuiteListPage
import com.yagay.ysuite.ui.YSuitePageState

@Composable
fun YFilesFeatureScreen(
    engine: YFilesEngine,
    logger: YSuiteLogger,
) {
    val model: YFilesViewModel = viewModel(
        factory = YFilesViewModelFactory(
            engine = engine,
            logger = logger,
        ),
    )
    val state by model.state.collectAsStateWithLifecycle()

    val pageState = when {
        state.loading && state.entries.isEmpty() ->
            YSuitePageState.Loading(
                message = stringResource(
                    R.string.yfiles_loading,
                ),
            )
        state.error != null ->
            YSuitePageState.Error(
                title = stringResource(
                    R.string.yfiles_error,
                ),
                message = state.error,
            )
        !state.loading && state.entries.isEmpty() ->
            YSuitePageState.Empty(
                title = stringResource(
                    R.string.yfiles_empty,
                ),
            )
        else ->
            YSuitePageState.Content
    }

    YSuiteListPage(
        title = stringResource(R.string.yfiles_title),
        subtitle = stringResource(
            R.string.yfiles_summary,
        ),
        state = pageState,
        header = {
            if (state.providers.isNotEmpty()) {
                YSuiteSection(
                    title = stringResource(
                        R.string.yfiles_sources,
                    ),
                ) {
                    YSuiteFilterBar(
                        options = state.providers.map {
                            YSuiteFilterOption(
                                id = it.id,
                                label = providerLabel(
                                    it.kind,
                                ),
                            )
                        },
                        selectedId =
                            state.activeProviderId,
                        onSelected =
                            model::selectProvider,
                    )
                }
            }

            state.directory?.let { directory ->
                YSuiteSection(
                    title = stringResource(
                        R.string.yfiles_location,
                    ),
                ) {
                    YSuiteListItem(
                        title = directory.path,
                        subtitle = directory.providerId,
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(
                            R.string.yfiles_parent,
                        ),
                        onClick = model::parent,
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(
                            R.string.yfiles_refresh,
                        ),
                        onClick = model::refresh,
                    )
                    YSuiteSearchField(
                        value = state.query,
                        onValueChange =
                            model::setQuery,
                        label = stringResource(
                            R.string.yfiles_search,
                        ),
                    )
                    YSuiteSwitchItem(
                        title = stringResource(
                            R.string.yfiles_recursive,
                        ),
                        checked = state.recursive,
                        onCheckedChange =
                            model::setRecursive,
                    )
                }
            }
        },
    ) { _ ->
        YSuiteSection(
            title = stringResource(
                R.string.yfiles_files,
            ),
        ) {
            state.entries.forEach { node ->
                YSuiteListItem(
                    title = node.name,
                    subtitle = nodeSubtitle(node),
                    modifier = if (
                        node.type ==
                            YFileType.Directory
                    ) {
                        Modifier.clickable {
                            model.open(node)
                        }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

@Composable
private fun providerLabel(
    kind: YFileProviderKind,
): String =
    when (kind) {
        YFileProviderKind.Local ->
            stringResource(
                R.string.yfiles_provider_local,
            )
        YFileProviderKind.Document ->
            stringResource(
                R.string.yfiles_provider_document,
            )
        YFileProviderKind.Root ->
            stringResource(
                R.string.yfiles_provider_root,
            )
        YFileProviderKind.Archive ->
            stringResource(
                R.string.yfiles_provider_archive,
            )
        YFileProviderKind.Remote ->
            stringResource(
                R.string.yfiles_provider_remote,
            )
    }

@Composable
private fun nodeSubtitle(
    node: YFileNode,
): String =
    when (node.type) {
        YFileType.Directory ->
            stringResource(
                R.string.yfiles_type_folder,
            )
        YFileType.File ->
            node.sizeBytes?.let(::formatBytes)
                ?: stringResource(
                    R.string.yfiles_type_file,
                )
        YFileType.SymbolicLink ->
            stringResource(
                R.string.yfiles_type_link,
            )
        YFileType.Other ->
            stringResource(
                R.string.yfiles_type_other,
            )
    }

private fun formatBytes(value: Long): String {
    if (value < 1024L) {
        return value.toString() + " B"
    }

    val units = arrayOf(
        "KiB",
        "MiB",
        "GiB",
        "TiB",
    )
    var amount = value.toDouble()
    var index = -1
    do {
        amount /= 1024.0
        index += 1
    } while (
        amount >= 1024.0 &&
        index < units.lastIndex
    )
    return String.format(
        "%.1f %s",
        amount,
        units[index],
    )
}

private class YFilesViewModelFactory(
    private val engine: YFilesEngine,
    private val logger: YSuiteLogger,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        YFilesViewModel(
            engine = engine,
            logger = logger,
        ) as T
}
