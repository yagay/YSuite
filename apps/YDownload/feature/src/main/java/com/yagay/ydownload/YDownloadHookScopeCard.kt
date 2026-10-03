package com.yagay.ydownload

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone

@Composable
fun YDownloadHookScopeCard(context: Context) {
    val candidates = remember(context) { YDownloadHookScopeAdvisor.browserCandidates(context) }
    YFeatureCard(
        title = stringResource(R.string.ydownload_hook_scope_title),
        subtitle = stringResource(R.string.ydownload_hook_scope_summary),
    ) {
        YStatusRow(
            stringResource(R.string.hook_scope_system_provider),
            stringResource(R.string.hook_scope_do_not_hook),
            YStatusTone.Good,
        )
        Text(stringResource(R.string.ydownload_hook_scope_rule))
        if (candidates.isEmpty()) {
            Text(stringResource(R.string.hook_scope_no_browser_candidates))
        } else {
            Text(stringResource(R.string.hook_scope_detected_browsers))
            candidates.take(8).forEach { candidate ->
                Text(stringResource(R.string.hook_scope_candidate_item, candidate.displayName))
            }
            if (candidates.size > 8) {
                Text(stringResource(R.string.hook_scope_more_candidates, candidates.size - 8))
            }
        }
        Text(stringResource(R.string.ydownload_hook_scope_note))
    }
}
