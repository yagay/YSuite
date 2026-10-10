package com.yagay.yui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Canonical full-screen modal page. Feature modules provide data/content only and never own
 * another Scaffold/TopAppBar/Dialog combination.
 */
@Composable
fun YFullScreenDialog(
    title: String,
    backContentDescription: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    role: YPageRole = YPageRole.DETAIL,
    actions: List<YActionSpec> = emptyList(),
    content: @Composable (PaddingValues) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        CompositionLocalProvider(LocalYPageRole provides role) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            YIconAction(
                                icon = YIcons.Back,
                                contentDescription = backContentDescription,
                                onClick = onDismissRequest,
                            )
                        },
                    )
                },
                bottomBar = {
                    if (actions.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                        ) {
                            YActionGroup(
                                actions = actions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = YDimens.ScreenHorizontal,
                                        vertical = YDimens.ControlGap,
                                    ),
                            )
                        }
                    }
                },
                content = content,
            )
        }
    }
}

/** Official Material 3 text actions shared by all normal dialogs. */
@Composable
fun YDialogConfirmButton(
    label: String,
    onClick: () -> Unit,
    dangerous: Boolean = false,
) {
    YUiTextButton(onClick = onClick, modifier = Modifier) {
        Text(
            label,
            color = if (dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun YDialogDismissButton(label: String, onClick: () -> Unit) {
    YUiTextButton(onClick = onClick) { Text(label) }
}

/** Canonical content dialog with YUI actions and spacing. */
@Composable
fun YFormDialog(
    title: String,
    onDismissRequest: () -> Unit,
    actions: List<YActionSpec>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
            ) { content() }
        },
        confirmButton = { YActionGroup(actions) },
    )
}
