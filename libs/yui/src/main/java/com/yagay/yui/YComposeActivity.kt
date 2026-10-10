package com.yagay.yui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/**
 * Standard Activity shell for every normal Compose screen in YSuite and standalone apps.
 *
 * Subclasses only provide feature initialization and composable content. Window/system-bar/theme
 * policy remains in YUI, so fixing it once fixes all apps.
 */
abstract class YComposeActivity : ComponentActivity() {
    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBeforeYContent(savedInstanceState)
        YView.applyComposeWindow(this)
        setContent {
            YTheme {
                YContent()
            }
        }
        onAfterYContent(savedInstanceState)
    }

    /**
     * YTheme resolves per-module appearance from the concrete Activity package.
     * Feature screens remain responsible only for content, not preference plumbing.
     */
    protected open fun onBeforeYContent(savedInstanceState: Bundle?) = Unit

    protected open fun onAfterYContent(savedInstanceState: Bundle?) = Unit

    @Composable
    protected abstract fun YContent()
}

/** Marker for transparent/overlay/probe Activities that must keep their own window semantics. */
interface YUiWindowOptOut
