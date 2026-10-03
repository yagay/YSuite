package com.yagay.yfiles

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone

@Composable
fun YFilesHookScopeCard(context: Context) {
    val targets = remember(context) { YFilesHookScopeAdvisor.recommendedTargets(context) }
    val pickers = targets.filter { it.kind == YFilesHookScopeAdvisor.TargetKind.PICKER }
    val callers = targets.filter { it.kind == YFilesHookScopeAdvisor.TargetKind.CALLER }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        YFeatureCard(
            title = stringResource(R.string.yfiles_hook_scope_title),
            subtitle = stringResource(R.string.yfiles_hook_scope_summary),
        ) {
            Text(stringResource(R.string.yfiles_scope_picker_targets))
            pickers.forEach { target ->
                YStatusRow(
                    target.displayName,
                    if (target.installed) {
                        stringResource(R.string.hook_scope_recommended)
                    } else {
                        stringResource(R.string.hook_scope_not_installed)
                    },
                    if (target.installed) YStatusTone.Good else YStatusTone.Neutral,
                )
            }
            Text(stringResource(R.string.yfiles_scope_picker_explanation))

            if (callers.isNotEmpty()) {
                Text(stringResource(R.string.yfiles_scope_optional_callers))
                callers.take(8).forEach { target ->
                    YStatusRow(
                        target.displayName,
                        stringResource(R.string.yfiles_scope_optional),
                        YStatusTone.Neutral,
                    )
                }
                if (callers.size > 8) {
                    Text(stringResource(R.string.yfiles_scope_more, callers.size - 8))
                }
                Text(stringResource(R.string.yfiles_scope_caller_explanation))
            }
            Text(stringResource(R.string.yfiles_hook_scope_rule))
            Text(stringResource(R.string.yfiles_hook_scope_note))
        }
        YFilesPowerToolsCard(context)
    }
}
