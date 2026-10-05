package com.yagay.ysuite.productui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

/**
 * The single normal-screen chrome owner for YSuite product UI.
 *
 * Product workspaces keep their mature product-specific information architecture, but do not
 * create their own app-level Scaffold/TopAppBar/insets. Browser and fullscreen products may own
 * their full surface intentionally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YSuiteProductTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        MaterialTheme.colorScheme.background,
                    scrolledContainerColor =
                        MaterialTheme.colorScheme.surfaceContainer,
                ),
        )
        HorizontalDivider(
            color =
                MaterialTheme.colorScheme.outlineVariant
                    .copy(alpha = 0.55f),
        )
    }
}

@Composable
fun YSuiteProductScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets =
        WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal +
                WindowInsetsSides.Bottom,
        ),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/**
 * Canonical page host for normal YSuite product screens.
 *
 * It owns the page Scaffold, system-bar insets and app bar. Product-specific tabs, breadcrumbs,
 * search/filter rows and panes are supplied as slots below the common top bar.
 */
@Composable
fun YSuiteProductPage(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    topBarOverride: (@Composable () -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductScaffold(
        modifier = modifier,
        topBar = {
            Column {
                if (topBarOverride != null) {
                    topBarOverride()
                } else {
                    YSuiteProductTopBar(
                        title = titleContent ?: { Text(title) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                    )
                }
                headerContent()
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
    ) { padding ->
        ProductAdaptiveBox(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) { adaptive ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                content(adaptive)
            }
        }
    }
}

/**
 * Shared bounded-content variant used by dashboards, tools and detail screens.
 */
@Composable
fun YSuiteBoundedProductPage(
    title: String,
    maxContentWidth: Dp,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductPage(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    ) { adaptive ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier =
                    Modifier
                        .widthIn(max = maxContentWidth)
                        .fillMaxSize()
                        .padding(YSuiteSpacing.Large),
            ) {
                content(adaptive)
            }
        }
    }
}
