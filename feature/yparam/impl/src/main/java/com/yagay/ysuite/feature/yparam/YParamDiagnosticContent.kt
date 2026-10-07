package com.yagay.ysuite.feature.yparam

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import androidx.compose.foundation.layout.padding

@Composable
internal fun YParamDiagnosticContent(
    state: YParamUiState,
) {
    val diagnostics =
        state.diagnostics ?: return

    YSuiteSection(
        title =
            stringResource(
                R.string.yparam_diagnostics,
            ),
        modifier =
            Modifier.padding(
                horizontal =
                    YSuiteSpacing.Medium,
            ),
    ) {
        YSuiteListItem(
            title =
                stringResource(
                    R.string.yparam_diag_version,
                ),
            subtitle =
                stringResource(
                    R.string
                        .yparam_diag_version_value,
                    diagnostics.versionName,
                    diagnostics.versionCode,
                ),
        )
        YSuiteListItem(
            title =
                stringResource(
                    R.string.yparam_diag_sdk,
                ),
            subtitle =
                stringResource(
                    R.string
                        .yparam_diag_sdk_value,
                    diagnostics.uid,
                    diagnostics.minSdk,
                    diagnostics.targetSdk,
                ),
        )
        YSuiteListItem(
            title =
                stringResource(
                    R.string
                        .yparam_diag_permissions,
                ),
            subtitle =
                diagnostics
                    .requestedPermissionCount
                    .toString(),
        )
        diagnostics.launchActivity?.let {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string
                            .yparam_diag_launch,
                    ),
                subtitle =
                    stringResource(
                        R.string
                            .yparam_diag_launch_value,
                        it,
                        diagnostics
                            .launchOrientation
                            ?: -1,
                    ),
            )
        }
        YSuiteListItem(
            title =
                stringResource(
                    R.string
                        .yparam_diag_display,
                ),
            subtitle =
                stringResource(
                    R.string
                        .yparam_diag_display_value,
                    diagnostics.widthPixels,
                    diagnostics.heightPixels,
                    diagnostics.densityDpi,
                ),
        )
        YSuiteListItem(
            title =
                stringResource(
                    R.string
                        .yparam_diag_configuration,
                ),
            subtitle =
                stringResource(
                    R.string
                        .yparam_diag_configuration_value,
                    diagnostics.screenWidthDp,
                    diagnostics.screenHeightDp,
                    diagnostics.smallestWidthDp,
                ),
        )
        YSuiteListItem(
            title =
                stringResource(
                    R.string
                        .yparam_diag_locale,
                ),
            subtitle =
                stringResource(
                    R.string
                        .yparam_diag_locale_value,
                    diagnostics.localeTag,
                    diagnostics.timeZoneId,
                ),
        )
        state.baseline?.let {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string
                            .yparam_baseline_snapshot,
                    ),
                subtitle = it,
            )
        }
    }
}
