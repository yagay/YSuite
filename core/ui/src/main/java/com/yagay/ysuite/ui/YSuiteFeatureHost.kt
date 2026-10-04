package com.yagay.ysuite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.resources.R
import kotlinx.coroutines.launch

private const val HOME_ID = "__home__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YSuiteFeatureHost(
    registry: YSuiteFeatureRegistry,
    modifier: Modifier = Modifier,
) {
    val features = registry.features
    var selectedId by rememberSaveable { mutableStateOf(HOME_ID) }

    BackHandler(enabled = selectedId != HOME_ID) {
        selectedId = HOME_ID
    }

    YSuiteAdaptiveContainer(modifier = modifier.fillMaxSize()) { widthClass ->
        if (widthClass == YSuiteWidthClass.Expanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                PermanentNavigationPane(
                    features = features,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                )
                Box(modifier = Modifier.weight(1f)) {
                    FeatureDestination(
                        selectedId = selectedId,
                        registry = registry,
                        onSelect = { selectedId = it },
                    )
                }
            }
        } else {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(YSuiteSpacing.Small),
                        ) {
                            NavigationItems(
                                features = features,
                                selectedId = selectedId,
                                onSelect = {
                                    selectedId = it
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }
                    }
                },
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            navigationIcon = {
                                if (selectedId == HOME_ID) {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(
                                            Icons.Default.Menu,
                                            contentDescription = stringResource(R.string.common_menu),
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { selectedId = HOME_ID }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.common_back),
                                        )
                                    }
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        FeatureDestination(
                            selectedId = selectedId,
                            registry = registry,
                            onSelect = { selectedId = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermanentNavigationPane(
    features: List<YSuiteFeatureUiRegistration>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(YSuiteLayoutTokens.NavigationPaneWidth)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(YSuiteSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(YSuiteSpacing.Small),
        )
        HorizontalDivider()
        NavigationItems(
            features = features,
            selectedId = selectedId,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun NavigationItems(
    features: List<YSuiteFeatureUiRegistration>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.common_home)) },
        selected = selectedId == HOME_ID,
        onClick = { onSelect(HOME_ID) },
        icon = { Icon(Icons.Default.Home, contentDescription = null) },
    )

    features.forEach { feature ->
        NavigationDrawerItem(
            label = { Text(feature.label()) },
            selected = selectedId == feature.contract.descriptor.id,
            onClick = { onSelect(feature.contract.descriptor.id) },
        )
    }
}

@Composable
private fun FeatureDestination(
    selectedId: String,
    registry: YSuiteFeatureRegistry,
    onSelect: (String) -> Unit,
) {
    if (selectedId == HOME_ID) {
        YSuiteFeatureDashboard(
            features = registry.features,
            onSelect = onSelect,
        )
        return
    }

    registry.findById(selectedId)?.Content()
        ?: YSuiteFeatureDashboard(
            features = registry.features,
            onSelect = onSelect,
        )
}

@Composable
private fun YSuiteFeatureDashboard(
    features: List<YSuiteFeatureUiRegistration>,
    onSelect: (String) -> Unit,
) {
    YSuiteDashboardPage(
        title = stringResource(R.string.home_title),
        subtitle = stringResource(R.string.home_summary),
    ) { _ ->
        if (features.isEmpty()) {
            YSuiteStateHost(
                state = YSuitePageState.Empty(
                    title = stringResource(R.string.common_empty),
                    message = stringResource(R.string.home_no_features),
                ),
            ) {}
        } else {
            features.forEach { feature ->
                YSuiteSection(title = feature.label()) {
                    YSuiteListItem(
                        title = feature.label(),
                        subtitle = feature.contract.descriptor.id,
                        modifier = Modifier.clickable {
                            onSelect(feature.contract.descriptor.id)
                        },
                    )
                }
            }
        }
    }
}
