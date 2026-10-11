package com.yagay.yui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.CompoundButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import kotlin.math.roundToInt

/** Colors shared by the Compose adapter and legacy View selectors. */
data class YSelectionControlColors(
    val active: Int,
    val inactive: Int,
    val disabled: Int,
)

/** One palette and minimum hit-target policy for both UI frameworks. */
object YSelectionControlStyle {
    fun fromView(context: Context) = YSelectionControlColors(
        active = YView.accent(context),
        inactive = YView.outline(context),
        disabled = YView.onSurfaceVariant(context),
    )

    fun apply(control: CompoundButton, colors: YSelectionControlColors) {
        fun fade(color: Int): Int {
            val alpha = (Color.alpha(color) * 0.38f).roundToInt().coerceIn(0, 255)
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        )
        control.buttonTintList = ColorStateList(states, intArrayOf(
            fade(colors.active),
            fade(colors.disabled),
            colors.active,
            colors.inactive,
        ))
        // Visual indicator can remain small, while all tappable selectors stay accessible.
        control.minimumHeight = maxOf(YView.touchTarget(control.context),
            YView.dp(control.context, 48))
        control.minimumWidth = maxOf(YView.touchTarget(control.context),
            YView.dp(control.context, 48))
    }
}

/**
 * This is the only native checkbox type instantiated by both Compose and Java/View.
 * YUI owns its shape, color, minimum target, and live theme preference updates.
 */
class YUiNativeCheckBox(context: Context) : MaterialCheckBox(context) {
    private val store = YAppearanceStore(context)
    private var composeColors: YSelectionControlColors? = null
    private val preferences = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        post {
            if (composeColors == null) refreshColors()
            requestLayout()
        }
    }

    init {
        isUseMaterialThemeColors = false
        refreshColors()
    }

    fun bindPresentation(colors: YSelectionControlColors) {
        if (composeColors != colors) {
            composeColors = colors
            refreshColors()
        }
    }

    private fun refreshColors() {
        YSelectionControlStyle.apply(this,
            composeColors ?: YSelectionControlStyle.fromView(context))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        store.registerPreferenceListener(preferences)
        refreshColors()
    }

    override fun onDetachedFromWindow() {
        store.unregisterPreferenceListener(preferences)
        super.onDetachedFromWindow()
    }
}

/**
 * One native Material radio control shared between Compose and RadioGroup screens.
 */
class YUiNativeRadioButton(context: Context) : MaterialRadioButton(context) {
    private val store = YAppearanceStore(context)
    private var composeColors: YSelectionControlColors? = null
    private val preferences = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        post {
            if (composeColors == null) refreshColors()
            requestLayout()
        }
    }

    init {
        isUseMaterialThemeColors = false
        refreshColors()
    }

    fun bindPresentation(colors: YSelectionControlColors) {
        if (composeColors != colors) {
            composeColors = colors
            refreshColors()
        }
    }

    private fun refreshColors() {
        YSelectionControlStyle.apply(this,
            composeColors ?: YSelectionControlStyle.fromView(context))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        store.registerPreferenceListener(preferences)
        refreshColors()
    }

    override fun onDetachedFromWindow() {
        store.unregisterPreferenceListener(preferences)
        super.onDetachedFromWindow()
    }
}
