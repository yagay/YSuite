package com.yagay.ysuite.feature.yfloat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.YFloat.ActionId
import com.yagay.YFloat.FloatSettings
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.featurelayout.YFloatWorkspace
import com.yagay.ysuite.productui.settings.ComposeSettingsChoice
import com.yagay.ysuite.productui.settings.ComposeSettingsChoiceGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsIntSlider
import com.yagay.ysuite.productui.settings.ComposeSettingsLink
import com.yagay.ysuite.productui.settings.ComposeSettingsSwitch
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun YFloatFeatureScreen(
    environment: YFloatEnvironment,
) {
    val model: YFloatViewModel =
        viewModel(
            factory =
                YFloatViewModel.Factory(
                    YFloatRepository(
                        environment.applicationContext,
                        environment.hookGateway,
                    ),
                ),
        )
    val state by
        model.state.collectAsStateWithLifecycle()
    val s = state.snapshot

    YFloatWorkspace(
        title =
            stringResource(
                R.string.yfloat_title,
            ),
        navigationIcon = {
            YSuiteHostNavigationButton()
        },
    ) {
        if (s == null) return@YFloatWorkspace

        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.yfloat_runtime,
                ),
        ) {
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string.yfloat_service,
                    ),
                subtitle =
                    stringResource(
                        R.string.yfloat_service_desc,
                    ),
                checked = s.serviceEnabled,
                onCheckedChange = model::service,
            )
            ComposeSettingsLink(
                title =
                    stringResource(
                        R.string.yfloat_overlay,
                    ),
                subtitle =
                    stringResource(
                        if (s.overlayPermission) {
                            R.string.yfloat_permission_ready
                        } else {
                            R.string.yfloat_permission_missing
                        },
                    ),
                onClick =
                    model::openOverlayPermission,
            )
            ComposeSettingsLink(
                title =
                    stringResource(
                        R.string.yfloat_accessibility,
                    ),
                subtitle =
                    stringResource(
                        if (
                            s.accessibilityConnected
                        ) {
                            R.string.yfloat_accessibility_ready
                        } else {
                            R.string.yfloat_accessibility_missing
                        },
                    ),
                onClick =
                    model::openAccessibility,
            )
        }

        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.yfloat_appearance,
                ),
        ) {
            ComposeSettingsIntSlider(
                title =
                    stringResource(
                        R.string.yfloat_alpha,
                    ),
                value = s.alphaPercent,
                range = 10..100,
                onValueChange = {
                    model.int(
                        FloatSettings.K_ALPHA,
                        it,
                    )
                },
            )
            ComposeSettingsIntSlider(
                title =
                    stringResource(
                        R.string.yfloat_size,
                    ),
                value = s.sizeDp,
                range = 24..96,
                onValueChange = {
                    model.int(
                        FloatSettings.K_SIZE,
                        it,
                    )
                },
            )
            ComposeSettingsIntSlider(
                title =
                    stringResource(
                        R.string.yfloat_visible_percent,
                    ),
                value = s.showPercent,
                range = 10..100,
                onValueChange = {
                    model.int(
                        FloatSettings.K_SHOW_PERCENT,
                        it,
                    )
                },
            )
            switch(
                R.string.yfloat_both_sides,
                s.bothSide,
                FloatSettings.K_BOTH_SIDE,
                model,
            )
            switch(
                R.string.yfloat_snap,
                s.snap,
                FloatSettings.K_SNAP,
                model,
            )
            switch(
                R.string.yfloat_show_lock,
                s.showOnLock,
                FloatSettings.K_SHOW_ON_LOCK,
                model,
            )
            switch(
                R.string.yfloat_hide_fullscreen,
                s.hideFullscreen,
                FloatSettings.K_HIDE_FULLSCREEN,
                model,
            )
            switch(
                R.string.yfloat_ime_avoid,
                s.imeAvoid,
                FloatSettings.K_IME_AVOID,
                model,
            )
            switch(
                R.string.yfloat_quick_move,
                s.quickMove,
                FloatSettings.K_QUICK_MOVE,
                model,
            )
        }

        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.yfloat_gestures,
                ),
        ) {
            switch(
                R.string.yfloat_vibrate,
                s.vibrate,
                FloatSettings.K_VIBRATE,
                model,
            )
            switch(
                R.string.yfloat_track,
                s.track,
                FloatSettings.K_TRACK,
                model,
            )
            switch(
                R.string.yfloat_long_drag,
                s.longPressDrag,
                FloatSettings.K_LONG_PRESS_DRAG,
                model,
            )
        }
        actionGroup(
            R.string.yfloat_click_action,
            FloatSettings.K_ACTION_CLICK,
            s.clickAction,
            model,
        )
        actionGroup(
            R.string.yfloat_double_action,
            FloatSettings.K_ACTION_DOUBLE,
            s.doubleAction,
            model,
        )
        actionGroup(
            R.string.yfloat_long_action,
            FloatSettings.K_ACTION_LONG,
            s.longAction,
            model,
        )

        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.yfloat_capture_ocr,
                ),
        ) {
            switch(
                R.string.yfloat_keep_status,
                s.keepStatusBar,
                FloatSettings.K_KEEP_STATUS_BAR,
                model,
            )
            switch(
                R.string.yfloat_keep_navigation,
                s.keepNavigationBar,
                FloatSettings.K_KEEP_NAVIGATION_BAR,
                model,
            )
            switch(
                R.string.yfloat_accessibility_screenshot,
                s.accessibilityScreenshot,
                FloatSettings.K_ACCESSIBILITY_SCREENSHOT,
                model,
            )
            switch(
                R.string.yfloat_circle_border,
                s.circleBorder,
                FloatSettings.K_CIRCLE_BORDER_ENABLED,
                model,
            )
            ComposeSettingsIntSlider(
                title =
                    stringResource(
                        R.string.yfloat_circle_width,
                    ),
                value = s.circleBorderWidthDp,
                range = 1..8,
                onValueChange = {
                    model.int(
                        FloatSettings.K_CIRCLE_BORDER_WIDTH_DP,
                        it,
                    )
                },
            )
        }
        ComposeSettingsChoiceGroup(
            title =
                stringResource(
                    R.string.yfloat_circle_engine,
                ),
            selectedId =
                s.circleEngine.toString(),
            choices =
                listOf(
                    ComposeSettingsChoice(
                        "0",
                        stringResource(
                            R.string.yfloat_circle_own,
                        ),
                    ),
                    ComposeSettingsChoice(
                        "1",
                        stringResource(
                            R.string.yfloat_circle_google,
                        ),
                    ),
                ),
            onSelected = {
                model.int(
                    FloatSettings.K_CIRCLE_ENGINE,
                    it.toInt(),
                )
            },
        )
        engineGroup(
            title =
                stringResource(
                    R.string.yfloat_full_ocr_engine,
                ),
            selected = s.fullOcrEngine,
            key =
                FloatSettings
                    .K_CIRCLE_FULL_OCR_ENGINE,
            model = model,
        )
        engineGroup(
            title =
                stringResource(
                    R.string.yfloat_correction_engine,
                ),
            selected = s.correctionEngine,
            key =
                FloatSettings
                    .K_CIRCLE_CORRECTION_ENGINE,
            model = model,
        )

        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.yfloat_privileges,
                ),
        ) {
            switch(
                R.string.yfloat_enhanced_mode,
                s.enhancedMode,
                FloatSettings.K_ENHANCED_MODE,
                model,
            )
            switch(
                R.string.yfloat_root_enabled,
                s.rootEnabled,
                FloatSettings.K_ROOT_ENABLED,
                model,
            )
            ComposeSettingsLink(
                title =
                    stringResource(
                        R.string.yfloat_check_root,
                    ),
                subtitle =
                    stringResource(
                        if (s.rootGranted) {
                            R.string.yfloat_root_ready
                        } else {
                            R.string.yfloat_root_not_checked
                        },
                    ),
                onClick = model::checkRoot,
            )
            switch(
                R.string.yfloat_lsposed_enabled,
                s.lsposedEnabled,
                FloatSettings.K_LSPOSED_ENABLED,
                model,
            )
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string.yfloat_secure_screenshot,
                    ),
                subtitle =
                    state.hookSummary.ifBlank {
                        stringResource(
                            if (
                                s.hookStatus ==
                                CapabilityStatus.Available
                            ) {
                                R.string.yfloat_hook_ready
                            } else {
                                R.string.yfloat_hook_missing
                            },
                        )
                    },
                checked = s.secureScreenshot,
                onCheckedChange = {
                    model.bool(
                        FloatSettings
                            .K_LSPOSED_SECURE_SCREENSHOT,
                        it,
                    )
                },
            )
            ComposeSettingsLink(
                title =
                    stringResource(
                        R.string.yfloat_sync_hook_scope,
                    ),
                subtitle =
                    stringResource(
                        R.string.yfloat_sync_hook_scope_desc,
                    ),
                onClick = model::syncHooks,
            )
            switch(
                R.string.yfloat_diagnostic_logging,
                s.diagnosticLogging,
                FloatSettings.K_DIAGNOSTIC,
                model,
            )
        }

        state.statusToken?.let {
            ComposeSettingsLink(
                title = statusText(it),
                onClick = model::refresh,
            )
        }
    }
}

@Composable
private fun switch(
    titleRes: Int,
    checked: Boolean,
    key: String,
    model: YFloatViewModel,
) {
    ComposeSettingsSwitch(
        title = stringResource(titleRes),
        checked = checked,
        onCheckedChange = {
            model.bool(key, it)
        },
    )
}

@Composable
private fun actionGroup(
    titleRes: Int,
    key: String,
    selected: String,
    model: YFloatViewModel,
) {
    ComposeSettingsChoiceGroup(
        title = stringResource(titleRes),
        selectedId = selected,
        choices =
            listOf(
                ActionId.NONE,
                ActionId.BACK,
                ActionId.HOME,
                ActionId.RECENTS,
                ActionId.SCREENSHOT,
                ActionId.REGION_SCREENSHOT,
                ActionId.OCR,
                ActionId.NOTIFICATIONS,
                ActionId.HIDE,
                ActionId.MOVE_ICON,
                ActionId.AI_SCREEN,
            ).map {
                ComposeSettingsChoice(
                    it,
                    actionLabel(it),
                )
            },
        onSelected = {
            model.string(key, it)
        },
    )
}

@Composable
private fun engineGroup(
    title: String,
    selected: Int,
    key: String,
    model: YFloatViewModel,
) {
    ComposeSettingsChoiceGroup(
        title = title,
        selectedId = selected.toString(),
        choices =
            listOf(
                ComposeSettingsChoice(
                    "0",
                    stringResource(
                        R.string.yfloat_engine_auto,
                    ),
                ),
                ComposeSettingsChoice(
                    "1",
                    stringResource(
                        R.string.yfloat_engine_mlkit,
                    ),
                ),
                ComposeSettingsChoice(
                    "2",
                    stringResource(
                        R.string.yfloat_engine_paddle,
                    ),
                ),
                ComposeSettingsChoice(
                    "3",
                    stringResource(
                        R.string.yfloat_engine_google,
                    ),
                ),
            ),
        onSelected = {
            model.int(key, it.toInt())
        },
    )
}

@Composable
private fun actionLabel(id: String): String =
    when (id) {
        ActionId.NONE ->
            stringResource(
                R.string.yfloat_action_none,
            )
        ActionId.BACK ->
            stringResource(
                R.string.yfloat_action_back,
            )
        ActionId.HOME ->
            stringResource(
                R.string.yfloat_action_home,
            )
        ActionId.RECENTS ->
            stringResource(
                R.string.yfloat_action_recents,
            )
        ActionId.SCREENSHOT ->
            stringResource(
                R.string.yfloat_action_screenshot,
            )
        ActionId.REGION_SCREENSHOT ->
            stringResource(
                R.string.yfloat_action_region,
            )
        ActionId.OCR ->
            stringResource(
                R.string.yfloat_action_ocr,
            )
        ActionId.NOTIFICATIONS ->
            stringResource(
                R.string.yfloat_action_notifications,
            )
        ActionId.HIDE ->
            stringResource(
                R.string.yfloat_action_hide,
            )
        ActionId.MOVE_ICON ->
            stringResource(
                R.string.yfloat_action_move,
            )
        ActionId.AI_SCREEN ->
            stringResource(
                R.string.yfloat_action_ai,
            )
        else -> id
    }

@Composable
private fun statusText(token: String): String =
    when (token) {
        "root_granted" ->
            stringResource(
                R.string.yfloat_root_granted,
            )
        "root_denied" ->
            stringResource(
                R.string.yfloat_root_denied,
            )
        "hook_synced" ->
            stringResource(
                R.string.yfloat_hook_synced,
            )
        "hook_sync_failed" ->
            stringResource(
                R.string.yfloat_hook_sync_failed,
            )
        else -> token
    }
