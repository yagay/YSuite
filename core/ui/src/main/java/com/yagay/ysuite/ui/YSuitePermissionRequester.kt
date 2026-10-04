package com.yagay.ysuite.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yagay.ysuite.permissions.api.PermissionRequirement
import com.yagay.ysuite.permissions.api.PermissionResult
import com.yagay.ysuite.permissions.api.PermissionStatus

class YSuitePermissionRequester internal constructor(
    private val launchBlock: (List<PermissionRequirement>) -> Unit,
) {
    fun launch(requirements: List<PermissionRequirement>) {
        launchBlock(requirements)
    }
}

@Composable
fun rememberYSuitePermissionRequester(
    onResult: (PermissionResult) -> Unit,
): YSuitePermissionRequester {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        onResult(
            PermissionResult(
                statuses = results.mapValues { (_, granted) ->
                    if (granted) PermissionStatus.Granted else PermissionStatus.Denied
                },
            ),
        )
    }

    return remember(launcher) {
        YSuitePermissionRequester { requirements ->
            launcher.launch(
                requirements
                    .map(PermissionRequirement::permission)
                    .distinct()
                    .toTypedArray(),
            )
        }
    }
}
