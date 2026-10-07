package com.yagay.ysuite.productui.featurelayout

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.entity.LibCheckerWorkspace
import com.yagay.ysuite.productui.logs.LogcatReaderWorkspace
import com.yagay.ysuite.productui.nfc.NfcGateWorkspace
import com.yagay.ysuite.productui.settings.ComposeSettingsSurface
import com.yagay.ysuite.productui.task.AndroidTaskManagerWorkspace

@Composable
fun YEntryCleanerWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    searchAndFilters: @Composable () -> Unit = {},
    navigationPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    selectionBar: @Composable () -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) = LibCheckerWorkspace(title, modifier, navigationIcon, actions, searchAndFilters, navigationPane, detailPane, selectionBar, content)

@Composable
fun YPowerWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    searchAndFilters: @Composable () -> Unit = {},
    navigationPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) = LibCheckerWorkspace(title, modifier, navigationIcon, actions, searchAndFilters, navigationPane, detailPane, {}, content)

@Composable
fun YMiniGuardWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    searchAndFilters: @Composable () -> Unit = {},
    navigationPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) = LibCheckerWorkspace(title, modifier, navigationIcon, actions, searchAndFilters, navigationPane, detailPane, {}, content)

@Composable
fun YParamWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    searchAndFilters: @Composable () -> Unit = {},
    navigationPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) = LibCheckerWorkspace(title, modifier, navigationIcon, actions, searchAndFilters, navigationPane, detailPane, {}, content)

@Composable
fun YDiagWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    search: @Composable () -> Unit,
    filters: @Composable () -> Unit,
    details: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) = LogcatReaderWorkspace(title, modifier, navigationIcon, actions, search, filters, details, content)

@Composable
fun YNotifyWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    search: @Composable () -> Unit,
    filters: @Composable () -> Unit,
    details: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) = LogcatReaderWorkspace(title, modifier, navigationIcon, actions, search, filters, details, content)

@Composable
fun YNfcWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    status: @Composable () -> Unit = {},
    modes: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) = NfcGateWorkspace(title, modifier, navigationIcon, actions, status, modes, detailPane, content)

@Composable
fun YTaskManagerWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    summary: @Composable () -> Unit = {},
    filters: @Composable () -> Unit = {},
    resourcesPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) = AndroidTaskManagerWorkspace(title, modifier, navigationIcon, actions, summary, filters, resourcesPane, detailPane, content)

@Composable
fun YFloatWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    categoryPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable ColumnScope.(ProductAdaptiveInfo) -> Unit,
) = ComposeSettingsSurface(title, modifier, navigationIcon, actions, categoryPane, content)
