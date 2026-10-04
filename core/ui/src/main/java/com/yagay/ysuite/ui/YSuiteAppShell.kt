package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.resources.R

enum class YSuiteHostNavigationIcon {
    None,
    Menu,
    Back,
}

data class YSuiteHostNavigationState(
    val icon: YSuiteHostNavigationIcon = YSuiteHostNavigationIcon.None,
    val onClick: () -> Unit = {},
)

val LocalYSuiteHostNavigation =
    staticCompositionLocalOf { YSuiteHostNavigationState() }

@Composable
fun YSuiteHostNavigationButton() {
    val navigation = LocalYSuiteHostNavigation.current
    when (navigation.icon) {
        YSuiteHostNavigationIcon.None -> Unit
        YSuiteHostNavigationIcon.Menu ->
            IconButton(onClick = navigation.onClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.common_menu),
                )
            }
        YSuiteHostNavigationIcon.Back ->
            IconButton(onClick = navigation.onClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YSuiteStandardTopBar(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            YSuiteHostNavigationButton()
        },
        actions = actions,
    )
}

@Composable
fun YSuiteAppShell(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            when {
                topBar != null -> topBar()
                title != null ->
                    YSuiteStandardTopBar(
                        title = title,
                        subtitle = subtitle,
                        actions = actions,
                    )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            floatingActionButton?.invoke()
        },
        content = content,
    )
}
