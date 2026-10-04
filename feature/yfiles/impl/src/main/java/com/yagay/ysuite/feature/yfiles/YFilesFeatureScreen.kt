package com.yagay.ysuite.feature.yfiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.component.YSuiteSwitchItem
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFileProperties
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFilesRepository
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.ui.YSuiteListPage
import com.yagay.ysuite.ui.YSuitePageState
import java.text.DateFormat

@Composable
fun YFilesFeatureScreen(
    repository: YFilesRepository,
    logger: YSuiteLogger,
) {
    val model: YFilesViewModel = viewModel(
        factory = YFilesViewModelFactory(
            repository = repository,
            logger = logger,
        ),
    )
    val state by model.state.collectAsStateWithLifecycle()

    val pageState = when {
        state.loading && state.entries.isEmpty() ->
            YSuitePageState.Loading(
                stringResource(R.string.yfiles_loading),
            )
        state.error != null ->
            YSuitePageState.Error(
                title = stringResource(R.string.yfiles_error),
                message = state.error,
                retryText = stringResource(R.string.yfiles_retry),
            )
        !state.loading && state.entries.isEmpty() ->
            YSuitePageState.Empty(
                title = stringResource(R.string.yfiles_empty),
            )
        else -> YSuitePageState.Content
    }

    YSuiteListPage(
        title = stringResource(R.string.yfiles_title),
        subtitle = stringResource(R.string.yfiles_summary),
        state = pageState,
        header = {
            BrowserControls(
                state = state,
                onParent = model::parent,
                onRefresh = model::refresh,
                onQuery = model::setQuery,
                onRecursive = model::setRecursive,
                onHidden = model::setShowHidden,
                onSort = model::setSort,
                onDescending = model::setDescending,
            )
        },
    ) { _ ->
        FileList(
            entries = state.entries,
            selectedPaths = state.selectedPaths,
            onOpen = model::open,
            onSelect = model::toggleSelection,
        )

        state.properties?.let {
            PropertiesSection(properties = it)
        }
    }
}

@Composable
private fun BrowserControls(
    state: YFilesUiState,
    onParent: () -> Unit,
    onRefresh: () -> Unit,
    onQuery: (String) -> Unit,
    onRecursive: (Boolean) -> Unit,
    onHidden: (Boolean) -> Unit,
    onSort: (YFileSort) -> Unit,
    onDescending: (Boolean) -> Unit,
) {
    YSuiteSection(
        title = stringResource(R.string.yfiles_location),
    ) {
        YSuiteListItem(title = state.path)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            YSuiteSecondaryButton(
                text = stringResource(R.string.yfiles_parent),
                onClick = onParent,
            )
            YSuiteSecondaryButton(
                text = stringResource(R.string.yfiles_refresh),
                onClick = onRefresh,
            )
        }

        YSuiteSearchField(
            value = state.query,
            onValueChange = onQuery,
            label = stringResource(R.string.yfiles_search),
        )

        YSuiteSwitchItem(
            title = stringResource(R.string.yfiles_recursive),
            checked = state.recursive,
            onCheckedChange = onRecursive,
        )
        YSuiteSwitchItem(
            title = stringResource(R.string.yfiles_show_hidden),
            checked = state.showHidden,
            onCheckedChange = onHidden,
        )

        YSuiteListItem(
            title = stringResource(R.string.yfiles_sort),
        )
        YSuiteFilterBar(
            options = listOf(
                YSuiteFilterOption(
                    YFileSort.Name.name,
                    stringResource(R.string.yfiles_sort_name),
                ),
                YSuiteFilterOption(
                    YFileSort.Modified.name,
                    stringResource(R.string.yfiles_sort_modified),
                ),
                YSuiteFilterOption(
                    YFileSort.Size.name,
                    stringResource(R.string.yfiles_sort_size),
                ),
                YSuiteFilterOption(
                    YFileSort.Type.name,
                    stringResource(R.string.yfiles_sort_type),
                ),
            ),
            selectedId = state.sort.name,
            onSelected = {
                onSort(YFileSort.valueOf(it))
            },
        )
        YSuiteFilterBar(
            options = listOf(
                YSuiteFilterOption(
                    "ascending",
                    stringResource(R.string.yfiles_ascending),
                ),
                YSuiteFilterOption(
                    "descending",
                    stringResource(R.string.yfiles_descending),
                ),
            ),
            selectedId = if (state.descending) {
                "descending"
            } else {
                "ascending"
            },
            onSelected = {
                onDescending(it == "descending")
            },
        )

        if (state.selectedPaths.isNotEmpty()) {
            YSuiteStatusBadge(
                text = stringResource(
                    R.string.yfiles_selected_count,
                    state.selectedPaths.size,
                ),
                tone = YSuiteStatusTone.Positive,
            )
        }
    }
}

@Composable
private fun FileList(
    entries: List<YFileEntry>,
    selectedPaths: Set<String>,
    onOpen: (YFileEntry) -> Unit,
    onSelect: (YFileEntry) -> Unit,
) {
    YSuiteSection(
        title = stringResource(R.string.yfiles_files),
    ) {
        entries.forEach { entry ->
            val selected = entry.path in selectedPaths
            YSuiteListItem(
                title = entry.name,
                subtitle = entrySubtitle(entry),
                modifier = Modifier.clickable {
                    onOpen(entry)
                },
                trailing = {
                    YSuiteSecondaryButton(
                        text = stringResource(
                            if (selected) {
                                R.string.yfiles_unselect
                            } else {
                                R.string.yfiles_select
                            },
                        ),
                        onClick = { onSelect(entry) },
                    )
                },
            )
        }
    }
}

@Composable
private fun PropertiesSection(
    properties: YFileProperties,
) {
    YSuiteSection(
        title = stringResource(R.string.yfiles_details),
    ) {
        YSuiteListItem(
            title = properties.name,
            subtitle = if (properties.directory) {
                stringResource(R.string.yfiles_folder)
            } else {
                stringResource(R.string.yfiles_file)
            },
        )
        YSuiteListItem(
            title = stringResource(R.string.yfiles_path),
            subtitle = properties.path,
        )
        YSuiteListItem(
            title = stringResource(R.string.yfiles_size),
            subtitle = formatBytes(properties.sizeBytes),
        )
        YSuiteListItem(
            title = stringResource(R.string.yfiles_modified),
            subtitle = formatDate(properties.modifiedAtMillis),
        )
        YSuiteListItem(
            title = stringResource(R.string.yfiles_access),
            subtitle = stringResource(
                R.string.yfiles_access_value,
                yesNo(properties.readable),
                yesNo(properties.writable),
                yesNo(properties.executable),
            ),
        )
        properties.childCount?.let {
            YSuiteListItem(
                title = stringResource(R.string.yfiles_children),
                subtitle = it.toString(),
            )
        }
    }
}

@Composable
private fun entrySubtitle(
    entry: YFileEntry,
): String =
    if (entry.directory) {
        stringResource(R.string.yfiles_folder)
    } else {
        formatBytes(entry.sizeBytes) +
            " · " +
            formatDate(entry.modifiedAtMillis)
    }

@Composable
private fun yesNo(value: Boolean): String =
    stringResource(
        if (value) {
            R.string.yfiles_yes
        } else {
            R.string.yfiles_no
        },
    )

private fun formatDate(value: Long): String =
    if (value <= 0L) {
        "-"
    } else {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
        ).format(value)
    }

private fun formatBytes(value: Long): String {
    if (value < 1024L) return value.toString() + " B"

    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var amount = value.toDouble()
    var index = -1
    do {
        amount /= 1024.0
        index += 1
    } while (
        amount >= 1024.0 &&
        index < units.lastIndex
    )

    return String.format("%.1f %s", amount, units[index])
}

private class YFilesViewModelFactory(
    private val repository: YFilesRepository,
    private val logger: YSuiteLogger,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        YFilesViewModel(
            repository = repository,
            logger = logger,
        ) as T
}
