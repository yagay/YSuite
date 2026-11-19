package com.yagay.YSuite

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.suite.core.SuiteCommonSetting
import com.yagay.suite.core.SuiteCommonSettings
import com.yagay.yui.YListItem
import com.yagay.yui.YPageList
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSection
import com.yagay.yui.YSettingsScaffold
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YUiRadioButton

/** Global application behavior, separate from module features and the YUI appearance catalog. */
@Composable
internal fun SuiteGeneralSettingsScreen(
    onBack: () -> Unit,
    onOpenSharedSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var target: Context = context
        while (target is ContextWrapper && target !is Activity) target = target.baseContext
        target as? Activity
    }
    val preferences = remember(context.applicationContext) { SuiteCommonSettings(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val close = preferences.observe { revision++ }
        onDispose(close)
    }
    // The OS is authoritative on Android 13+, including changes from Android's App Languages.
    val locale = remember(context, revision) { SuiteLocaleController.selected(context) }
    val hideDisabled = remember(preferences, revision) {
        preferences.value(SuiteCommonSetting.HOME_HIDE_DISABLED) == "true"
    }
    val showSearch = remember(preferences, revision) {
        preferences.value(SuiteCommonSetting.HOME_SHOW_SEARCH) == "true"
    }
    val showDiagnostics = remember(preferences, revision) {
        preferences.value(SuiteCommonSetting.HOME_SHOW_DIAGNOSTICS) == "true"
    }
    val version = remember(context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty()
    }

    YSettingsScaffold(
        title = stringResource(R.string.general_title),
        subtitle = stringResource(R.string.general_subtitle),
    ) { padding ->
        YPageList(padding = padding) {
            item(key = "back") {
                YSecondaryButton(stringResource(R.string.settings_back), onClick = onBack)
            }
            item(key = "language") {
                YSection(
                    title = stringResource(R.string.general_language_title),
                    subtitle = stringResource(R.string.general_language_desc),
                ) {
                    Column {
                        listOf(
                            SuiteLocaleController.SYSTEM to R.string.general_language_system,
                            SuiteLocaleController.CHINESE to R.string.general_language_chinese,
                            SuiteLocaleController.ENGLISH to R.string.general_language_english,
                        ).forEach { (tag, label) ->
                            YListItem(
                                title = stringResource(label),
                                selected = locale == tag,
                                leading = {
                                    YUiRadioButton(selected = locale == tag, onClick = null)
                                },
                                onClick = {
                                    if (tag != locale) activity?.let {
                                        SuiteLocaleController.set(it, tag)
                                        revision++
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item(key = "home") {
                YSection(
                    title = stringResource(R.string.general_home_title),
                    subtitle = stringResource(R.string.general_home_desc),
                ) {
                    YSwitchItem(
                        title = stringResource(R.string.general_hide_disabled),
                        subtitle = stringResource(R.string.general_hide_disabled_desc),
                        checked = hideDisabled,
                        onCheckedChange = {
                            preferences.set(SuiteCommonSetting.HOME_HIDE_DISABLED, it.toString())
                            revision++
                        },
                    )
                    YSwitchItem(
                        title = stringResource(R.string.general_show_search),
                        subtitle = stringResource(R.string.general_show_search_desc),
                        checked = showSearch,
                        onCheckedChange = {
                            preferences.set(SuiteCommonSetting.HOME_SHOW_SEARCH, it.toString())
                            revision++
                        },
                    )
                    YSwitchItem(
                        title = stringResource(R.string.general_show_diagnostics),
                        subtitle = stringResource(R.string.general_show_diagnostics_desc),
                        checked = showDiagnostics,
                        onCheckedChange = {
                            preferences.set(SuiteCommonSetting.HOME_SHOW_DIAGNOSTICS, it.toString())
                            revision++
                        },
                    )
                }
            }
            item(key = "about") {
                YSection(title = stringResource(R.string.general_about_title)) {
                    YListItem(
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.general_version, version),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        },
                    )
                    Text(
                        text = stringResource(R.string.general_about_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    YSecondaryButton(
                        text = stringResource(R.string.general_shared_settings),
                        onClick = onOpenSharedSettings,
                    )
                }
            }
        }
    }
}
