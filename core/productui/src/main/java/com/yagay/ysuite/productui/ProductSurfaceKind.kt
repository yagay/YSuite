package com.yagay.ysuite.productui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class ProductSurfaceKind {
    Dashboard,
    FileManager,
    Browser,
    Settings,
    LogViewer,
    DownloadManager,
    TaskManager,
    AutomationStudio,
    EntityManager,
    Tool,
    Detail,
    Fullscreen,
}

val LocalProductSurfaceKind =
    staticCompositionLocalOf<ProductSurfaceKind?> { null }

@Composable
fun ProductSurfaceScope(
    surfaceKind: ProductSurfaceKind,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalProductSurfaceKind provides surfaceKind,
        content = content,
    )
}
