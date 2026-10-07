package com.yagay.ysuite.feature.yparam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteFilterBar
import com.yagay.ysuite.designsystem.component.YSuiteFilterOption
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuitePrimaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSearchField
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.feature.yparam.api.YParamDefaults
import com.yagay.ysuite.feature.yparam.api.YParamOverrides
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.featurelayout.YParamWorkspace
import com.yagay.ysuite.ui.YSuiteFeatureBackHandler
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun YParamFeatureScreen(
    environment: YParamEnvironment,
) {
    val model: YParamViewModel =
        viewModel(factory = YParamViewModel.Factory(environment))
    val state by model.state.collectAsStateWithLifecycle()
    val selected =
        state.apps.firstOrNull {
            it.packageName == state.selectedPackage
        }

    if (state.selectedPackage != null) {
        YSuiteFeatureBackHandler(onBack = { model.select(null) })
    }

    YParamWorkspace(
        title = stringResource(R.string.yparam_title),
        navigationIcon = { YSuiteHostNavigationButton() },
        searchAndFilters = {
            Column(
                modifier = Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                YSuiteSearchField(
                    value = state.query,
                    onValueChange = model::setQuery,
                    label = stringResource(R.string.yparam_search),
                )
                YSuiteFilterBar(
                    options =
                        YParamAppFilter.entries.map {
                            YSuiteFilterOption(
                                it.name,
                                filterLabel(it),
                            )
                        },
                    selectedId = state.filter.name,
                    onSelected = {
                        runCatching {
                            YParamAppFilter.valueOf(it)
                        }.getOrNull()?.let(model::setFilter)
                    },
                )
            }
        },
        navigationPane = {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.yparam_apps),
                        subtitle =
                            stringResource(
                                R.string.yparam_app_count,
                                state.apps.size,
                            ),
                    )
                }
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.yparam_overridden),
                        subtitle =
                            state.apps.count {
                                it.overrideCount > 0
                            }.toString(),
                    )
                }
            }
        },
        detailPane =
            selected?.let {
                {
                    ParamDetail(
                        adaptive = it,
                        appLabel = selected.label,
                        packageName = selected.packageName,
                        state = state,
                        model = model,
                    )
                }
            },
    ) { adaptive ->
        if (!adaptive.isExpanded && selected != null) {
            ParamDetail(
                adaptive = adaptive,
                appLabel = selected.label,
                packageName = selected.packageName,
                state = state,
                model = model,
            )
        } else {
            AppList(model)
        }
    }
}

@Composable
private fun AppList(model: YParamViewModel) {
    val apps = model.visibleApps()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (apps.isEmpty()) {
            item {
                YSuiteListItem(
                    title = stringResource(R.string.yparam_no_apps),
                    modifier = Modifier.padding(YSuiteSpacing.Medium),
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            YSuiteListItem(
                title = app.label,
                subtitle =
                    stringResource(
                        R.string.yparam_app_summary,
                        app.packageName,
                        app.overrideCount,
                    ),
                modifier =
                    Modifier
                        .clickable { model.select(app.packageName) }
                        .padding(
                            horizontal = YSuiteSpacing.Medium,
                            vertical = YSuiteSpacing.Small,
                        ),
            )
        }
    }
}

@Composable
private fun ParamDetail(
    adaptive: ProductAdaptiveInfo,
    appLabel: String,
    packageName: String,
    state: YParamUiState,
    model: YParamViewModel,
) {
    val defaults = state.defaults ?: return
    val value = state.draft
    val fields = parameterItems(
        value = value,
        defaults = defaults,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
    ) {
        item {
            YSuiteSection(
                title = appLabel,
                modifier = Modifier.padding(YSuiteSpacing.Medium),
            ) {
                YSuiteListItem(
                    title = packageName,
                    subtitle =
                        stringResource(
                            R.string.yparam_override_count,
                            value.overrideCount(),
                        ),
                )
                YSuiteStatusBadge(
                    text =
                        if (state.hookStatus == CapabilityStatus.Available) {
                            stringResource(R.string.yparam_hook_ready)
                        } else {
                            stringResource(R.string.yparam_hook_unavailable)
                        },
                    tone =
                        if (state.hookStatus == CapabilityStatus.Available) {
                            YSuiteStatusTone.Positive
                        } else {
                            YSuiteStatusTone.Warning
                        },
                )
                YSuiteFilterBar(
                    options =
                        listOf(
                            YSuiteFilterOption(
                                "tablet",
                                stringResource(R.string.yparam_preset_tablet),
                            ),
                            YSuiteFilterOption(
                                "compact",
                                stringResource(R.string.yparam_preset_compact),
                            ),
                            YSuiteFilterOption(
                                "uk",
                                stringResource(R.string.yparam_preset_uk),
                            ),
                        ),
                    selectedId = null,
                    onSelected = model::applyPreset,
                )
            }
        }

        fields.forEach { field ->
            item(key = field.key) {
                ParameterEditor(field, model::update)
            }
        }

        item {
            YParamDiagnosticContent(state)
        }

        item {
            Column(
                modifier = Modifier.padding(YSuiteSpacing.Medium),
                verticalArrangement =
                    Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                state.message?.let {
                    YSuiteStatusBadge(
                        text = messageText(it),
                        tone =
                            if (it == "saved") {
                                YSuiteStatusTone.Positive
                            } else {
                                YSuiteStatusTone.Warning
                            },
                    )
                }
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(YSuiteSpacing.Small),
                ) {
                    YSuitePrimaryButton(
                        text = stringResource(R.string.yparam_save),
                        onClick = model::save,
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yparam_restore_all),
                        onClick = model::reset,
                    )
                }
            }
        }
    }
}

private data class ParameterField(
    val key: String,
    val title: String,
    val defaultValue: String,
    val value: String?,
    val presets: List<String>,
)

@Composable
private fun ParameterEditor(
    field: ParameterField,
    onUpdate: (String, String?) -> Unit,
) {
    YSuiteSection(
        title = field.title,
        modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
    ) {
        YSuiteListItem(
            title = stringResource(R.string.yparam_default_value),
            subtitle = field.defaultValue,
        )
        if (field.presets.isNotEmpty()) {
            val options =
                listOf(
                    YSuiteFilterOption(
                        "__default__",
                        stringResource(R.string.yparam_default),
                    ),
                ) + field.presets.map {
                    YSuiteFilterOption(it, it)
                }
            YSuiteFilterBar(
                options = options,
                selectedId = field.value ?: "__default__",
                onSelected = {
                    onUpdate(
                        field.key,
                        it.takeUnless { id ->
                            id == "__default__"
                        },
                    )
                },
            )
        }
        YSuiteSearchField(
            value = field.value.orEmpty(),
            onValueChange = { onUpdate(field.key, it) },
            label = stringResource(R.string.yparam_custom_value),
        )
        if (field.value != null) {
            YSuiteSecondaryButton(
                text = stringResource(R.string.yparam_restore),
                onClick = { onUpdate(field.key, null) },
            )
        }
    }
}

@Composable
private fun parameterItems(
    value: YParamOverrides,
    defaults: YParamDefaults,
): List<ParameterField> =
    listOf(
        ParameterField(
            "densityDpi",
            stringResource(R.string.yparam_density),
            defaults.densityDpi.toString(),
            value.densityDpi?.toString(),
            listOf("120", "160", "213", "240", "280", "320", "360", "380", "400", "420", "440", "480", "560", "640"),
        ),
        ParameterField(
            "widthPixels",
            stringResource(R.string.yparam_width),
            defaults.widthPixels.toString(),
            value.widthPixels?.toString(),
            listOf("720", "900", "1080", "1200", "1440"),
        ),
        ParameterField(
            "heightPixels",
            stringResource(R.string.yparam_height),
            defaults.heightPixels.toString(),
            value.heightPixels?.toString(),
            listOf("1280", "1600", "1920", "2160", "2400", "2520", "2772", "3120", "3200"),
        ),
        ParameterField(
            "smallestWidthDp",
            stringResource(R.string.yparam_smallest_width),
            defaults.smallestWidthDp.toString(),
            value.smallestWidthDp?.toString(),
            listOf("320", "360", "392", "411", "480", "600", "720", "840"),
        ),
        ParameterField(
            "screenWidthDp",
            stringResource(R.string.yparam_screen_width),
            defaults.screenWidthDp.toString(),
            value.screenWidthDp?.toString(),
            listOf("320", "360", "392", "411", "480", "600", "720", "840"),
        ),
        ParameterField(
            "screenHeightDp",
            stringResource(R.string.yparam_screen_height),
            defaults.screenHeightDp.toString(),
            value.screenHeightDp?.toString(),
            listOf("640", "720", "800", "840", "960", "1080", "1280"),
        ),
        ParameterField(
            "fontScale",
            stringResource(R.string.yparam_font_scale),
            defaults.fontScale.toString(),
            value.fontScale?.toString(),
            listOf("0.80", "0.90", "0.95", "1.00", "1.05", "1.10", "1.20", "1.30"),
        ),
        ParameterField(
            "xdpi",
            stringResource(R.string.yparam_xdpi),
            defaults.xdpi.toString(),
            value.xdpi?.toString(),
            emptyList(),
        ),
        ParameterField(
            "ydpi",
            stringResource(R.string.yparam_ydpi),
            defaults.ydpi.toString(),
            value.ydpi?.toString(),
            emptyList(),
        ),
        ParameterField(
            "refreshRate",
            stringResource(R.string.yparam_refresh_rate),
            defaults.refreshRate?.toString().orEmpty(),
            value.refreshRate?.toString(),
            listOf("60", "90", "120", "144", "165"),
        ),
        ParameterField(
            "localeTag",
            stringResource(R.string.yparam_locale),
            defaults.localeTag,
            value.localeTag,
            listOf("zh-CN", "zh-TW", "en-GB", "en-US", "ja-JP", "ko-KR", "de-DE", "fr-FR"),
        ),
        ParameterField(
            "timeZoneId",
            stringResource(R.string.yparam_timezone),
            defaults.timeZoneId,
            value.timeZoneId,
            listOf("UTC", "Asia/Shanghai", "Asia/Hong_Kong", "Asia/Tokyo", "Europe/London", "Europe/Paris", "America/New_York", "America/Los_Angeles"),
        ),
        ParameterField(
            "nightMode",
            stringResource(R.string.yparam_night_mode),
            defaults.nightMode,
            value.nightMode,
            listOf("light", "dark"),
        ),
        ParameterField(
            "orientation",
            stringResource(R.string.yparam_orientation),
            defaults.orientation,
            value.orientation,
            listOf("portrait", "landscape", "sensor", "locked"),
        ),
        ParameterField(
            "allowScreenshots",
            stringResource(R.string.yparam_screenshots),
            stringResource(R.string.yparam_app_default),
            value.allowScreenshots?.toString(),
            listOf("true", "false"),
        ),
        ParameterField(
            "keepScreenOn",
            stringResource(R.string.yparam_keep_screen_on),
            stringResource(R.string.yparam_app_default),
            value.keepScreenOn?.toString(),
            listOf("true", "false"),
        ),
        ParameterField(
            "userAgent",
            stringResource(R.string.yparam_user_agent),
            stringResource(R.string.yparam_app_default),
            value.userAgent,
            emptyList(),
        ),
        ParameterField(
            "locationMode",
            stringResource(R.string.yparam_location_mode),
            stringResource(R.string.yparam_real_location),
            value.locationMode,
            listOf("fixed", "random"),
        ),
        ParameterField(
            "latitude",
            stringResource(R.string.yparam_latitude),
            stringResource(R.string.yparam_real_location),
            value.latitude?.toString(),
            emptyList(),
        ),
        ParameterField(
            "longitude",
            stringResource(R.string.yparam_longitude),
            stringResource(R.string.yparam_real_location),
            value.longitude?.toString(),
            emptyList(),
        ),
        ParameterField(
            "altitude",
            stringResource(R.string.yparam_altitude),
            stringResource(R.string.yparam_original_value),
            value.altitude?.toString(),
            emptyList(),
        ),
        ParameterField(
            "accuracy",
            stringResource(R.string.yparam_accuracy),
            stringResource(R.string.yparam_original_value),
            value.accuracy?.toString(),
            listOf("3", "5", "10", "20", "50", "100"),
        ),
        ParameterField(
            "speed",
            stringResource(R.string.yparam_speed),
            stringResource(R.string.yparam_original_value),
            value.speed?.toString(),
            listOf("0", "1.4", "5", "10", "20", "30"),
        ),
        ParameterField(
            "bearing",
            stringResource(R.string.yparam_bearing),
            stringResource(R.string.yparam_original_value),
            value.bearing?.toString(),
            listOf("0", "45", "90", "135", "180", "225", "270", "315"),
        ),
        ParameterField(
            "randomRadiusMeters",
            stringResource(R.string.yparam_random_radius),
            "0",
            value.randomRadiusMeters?.toString(),
            listOf("0", "10", "50", "100", "500", "1000", "5000"),
        ),
        ParameterField(
            "locationUpdateIntervalMs",
            stringResource(R.string.yparam_update_interval),
            "5000",
            value.locationUpdateIntervalMs?.toString(),
            listOf("1000", "3000", "5000", "10000", "30000", "60000"),
        ),
    )

@Composable
private fun filterLabel(value: YParamAppFilter): String =
    when (value) {
        YParamAppFilter.All -> stringResource(R.string.yparam_filter_all)
        YParamAppFilter.User -> stringResource(R.string.yparam_filter_user)
        YParamAppFilter.System -> stringResource(R.string.yparam_filter_system)
        YParamAppFilter.Overridden -> stringResource(R.string.yparam_filter_overridden)
    }

@Composable
private fun messageText(value: String): String =
    when (value) {
        "saved" -> stringResource(R.string.yparam_saved)
        "saved_hook_reload_unavailable" ->
            stringResource(R.string.yparam_saved_hook_unavailable)
        "validation_density" ->
            stringResource(R.string.yparam_validation_density)
        "validation_width" ->
            stringResource(R.string.yparam_validation_width)
        "validation_height" ->
            stringResource(R.string.yparam_validation_height)
        "validation_latitude" ->
            stringResource(R.string.yparam_validation_latitude)
        "validation_longitude" ->
            stringResource(R.string.yparam_validation_longitude)
        "validation_location" ->
            stringResource(R.string.yparam_validation_location)
        else -> value
    }
