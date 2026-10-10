package com.yagay.YSuite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YAppearanceSettings
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YPageList
import com.yagay.yui.YPageRole
import com.yagay.yui.YPageScaffold
import com.yagay.yui.YSection
import com.yagay.yui.YSecondaryButton

/**
 * Host-owned settings for visual preferences shared by all integrated feature screens.
 * Feature-only behavior remains on each module's dedicated settings page.
 */
class AppearanceSettingsActivity : YComposeActivity() {
    @Composable
    override fun YContent() {
        val context = LocalContext.current
        var selectedMode by remember { mutableIntStateOf(YAppearanceSettings.mode(context)) }

        fun selectMode(value: Int) {
            if (YAppearanceSettings.setMode(context, value)) {
                selectedMode = value
                recreate()
            }
        }

        YPageScaffold(
            title = stringResource(R.string.shared_appearance_title),
            role = YPageRole.SETTINGS,
            subtitle = stringResource(R.string.shared_appearance_desc),
        ) { contentPadding ->
            YPageList(padding = contentPadding) {
                item {
                    YSection(
                        title = stringResource(R.string.shared_appearance_theme),
                        subtitle = stringResource(R.string.shared_appearance_theme_desc),
                    ) {
                        YSecondaryButton(
                            text = (if (selectedMode == YAppearanceSettings.MODE_SYSTEM) "✓ " else "") +
                                stringResource(R.string.shared_appearance_system),
                            onClick = { selectMode(YAppearanceSettings.MODE_SYSTEM) },
                        )
                        YSecondaryButton(
                            text = (if (selectedMode == YAppearanceSettings.MODE_LIGHT) "✓ " else "") +
                                stringResource(R.string.shared_appearance_light),
                            onClick = { selectMode(YAppearanceSettings.MODE_LIGHT) },
                        )
                        YSecondaryButton(
                            text = (if (selectedMode == YAppearanceSettings.MODE_DARK) "✓ " else "") +
                                stringResource(R.string.shared_appearance_dark),
                            onClick = { selectMode(YAppearanceSettings.MODE_DARK) },
                        )
                    }
                }
            }
        }
    }
}
