package com.yagay.yfiles

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone

@Composable
fun YFilesHookScopeCard(context: Context) {
    val targets = remember(context) { YFilesHookScopeAdvisor.documentsUiTargets(context) }
    YFeatureCard(
        title = stringResource(R.string.yfiles_hook_scope_title),
        subtitle = stringResource(R.string.yfiles_hook_scope_summary),
    ) {
        targets.forEach { target ->
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
        Text(stringResource(R.string.yfiles_hook_scope_rule))
        Text(stringResource(R.string.yfiles_hook_scope_note))
    }
}
