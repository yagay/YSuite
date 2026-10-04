package com.yagay.ysuite.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
