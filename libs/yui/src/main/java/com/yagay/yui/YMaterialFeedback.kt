package com.yagay.yui

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Upstream Snackbar lifecycle, transitions and feedback semantics. */
@Composable
fun YUiSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (SnackbarData) -> Unit = { Snackbar(it) },
) {
    SnackbarHost(hostState = hostState, modifier = modifier, snackbar = snackbar)
}
