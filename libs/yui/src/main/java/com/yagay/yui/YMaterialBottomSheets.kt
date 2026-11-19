package com.yagay.yui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Upstream Material3 sheet with the shared YUI dialog radius. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YUiModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(LocalYAppearance.current.dialogRadiusDp.dp),
        content = content,
    )
}
