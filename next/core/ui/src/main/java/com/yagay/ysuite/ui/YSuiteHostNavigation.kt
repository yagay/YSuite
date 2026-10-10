package com.yagay.ysuite.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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
fun YSuiteBackNavigationButton(
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.common_back),
        )
    }
}

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

/**
 * Shared feature-level back handler.
 *
 * Feature implementations use this wrapper instead of depending directly on activity-compose.
 * Because feature content is composed after the host handler, enabled feature handlers consume
 * internal-page back before the YSuite host pops the feature destination.
 */
@Composable
fun YSuiteFeatureBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    BackHandler(
        enabled = enabled,
        onBack = onBack,
    )
}
