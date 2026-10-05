package com.yagay.ysuite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.productui.ProductAdaptiveRoot
import com.yagay.ysuite.productui.dashboard.NiaDashboardSurface
import com.yagay.ysuite.resources.R
import com.yagay.ysuite.runtime.FeatureLifecycleEvent
import kotlinx.coroutines.launch

private const val HOME_ROUTE = "__home__"

@Composable
fun YSuiteFeatureHost(
    registry: YSuiteFeatureRegistry,
    modifier: Modifier = Modifier,
    initialFeatureId: String? = null,
) {
    val features = registry.features
    val initialRoute =
        initialFeatureId
            ?.let(registry::findById)
            ?.contract
            ?.startRoute
            ?.value
    var backStack by rememberSaveable(initialFeatureId) {
        mutableStateOf(
            if (initialRoute != null) {
                listOf(HOME_ROUTE, initialRoute)
            } else {
                listOf(HOME_ROUTE)
            },
        )
    }
    val currentRoute = backStack.last()
    val activeFeature =
        currentRoute
            .takeUnless { it == HOME_ROUTE }
            ?.let(registry::findByRoute)
    val selectedId =
        activeFeature?.contract?.descriptor?.id ?: HOME_ROUTE

    fun navigateTo(id: String) {
        if (id == HOME_ROUTE) {
            backStack = topLevelDestinationStack(HOME_ROUTE, null)
            return
        }
        val target =
            registry.findById(id) ?: return
        val route = target.contract.startRoute.value
        if (route == currentRoute) return
        backStack = topLevelDestinationStack(HOME_ROUTE, route)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        }
    }

    DisposableEffect(activeFeature) {
        activeFeature?.lifecycleObserver?.onEvent(FeatureLifecycleEvent.Activated)
        onDispose {
            activeFeature?.lifecycleObserver?.onEvent(FeatureLifecycleEvent.Deactivated)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val permanentNavigation =
            maxWidth >= YSuiteLayoutTokens.ExpandedBreakpoint

        if (permanentNavigation) {
            Row(modifier = Modifier.fillMaxSize()) {
                PermanentNavigationPane(
                    features = features,
                    selectedId = selectedId,
                    onSelect = ::navigateTo,
                )
                ProductAdaptiveRoot(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    CompositionLocalProvider(
                        LocalYSuiteHostNavigation provides
                            YSuiteHostNavigationState(
                                icon =
                                    YSuiteHostNavigationIcon.None,
                                onClick = {},
                            ),
                    ) {
                        FeatureDestination(
                            selectedId = selectedId,
                            registry = registry,
                            onSelect = ::navigateTo,
                        )
                    }
                }
            }
        } else {
            val drawerState =
                rememberDrawerState(
                    initialValue = DrawerValue.Closed,
                )
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .verticalScroll(
                                        rememberScrollState(),
                                    )
                                    .padding(
                                        YSuiteSpacing.Medium,
                                    ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    YSuiteSpacing.Small,
                                ),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.app_name,
                                    ),
                                style =
                                    MaterialTheme.typography
                                        .headlineSmall,
                                modifier =
                                    Modifier.padding(
                                        YSuiteSpacing.Small,
                                    ),
                            )
                            NavigationItems(
                                features = features,
                                selectedId = selectedId,
                                onSelect = {
                                    navigateTo(it)
                                    scope.launch {
                                        drawerState.close()
                                    }
                                },
                            )
                        }
                    }
                },
            ) {
                val navState =
                    YSuiteHostNavigationState(
                        icon =
                            YSuiteHostNavigationIcon.Menu,
                        onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        },
                    )

                ProductAdaptiveRoot(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CompositionLocalProvider(
                        LocalYSuiteHostNavigation provides
                            navState,
                    ) {
                        FeatureDestination(
                            selectedId = selectedId,
                            registry = registry,
                            onSelect = ::navigateTo,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun YSuiteSingleFeatureHost(
    registry: YSuiteFeatureRegistry,
    modifier: Modifier = Modifier,
) {
    val feature = registry.features.singleOrNull()
        ?: error("Standalone host requires exactly one feature")

    DisposableEffect(feature) {
        feature.lifecycleObserver.onEvent(FeatureLifecycleEvent.Activated)
        onDispose {
            feature.lifecycleObserver.onEvent(FeatureLifecycleEvent.Deactivated)
        }
    }

    ProductAdaptiveRoot(
        modifier = modifier.fillMaxSize(),
    ) {
        CompositionLocalProvider(
            LocalYSuiteHostNavigation provides
                YSuiteHostNavigationState(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                feature.Content()
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
        selected = selectedId == HOME_ROUTE,
        onClick = { onSelect(HOME_ROUTE) },
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
    if (selectedId == HOME_ROUTE) {
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
    NiaDashboardSurface(
        title = stringResource(R.string.home_title),
        navigationIcon = { YSuiteHostNavigationButton() },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
        ) {
            items(
                items = features,
                key = { it.contract.descriptor.id },
            ) { feature ->
                ProductFeatureTile(
                    feature = feature,
                    onClick = { onSelect(feature.contract.descriptor.id) },
                )
            }
        }
    }
}

@Composable
private fun ProductFeatureTile(
    feature: YSuiteFeatureUiRegistration,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(YSuiteSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
        ) {
            Text(
                text = feature.label(),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

