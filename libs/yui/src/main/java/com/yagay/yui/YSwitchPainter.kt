package com.yagay.yui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One switch renderer shared by Compose (via drawIntoCanvas) and Android Views.
 * All visual bounds are read from YSwitchGeometry; the touch area is independent.
 */
data class YSwitchPaintColors(
    val checkedTrack: Int,
    val uncheckedTrack: Int,
    val checkedThumb: Int,
    val uncheckedThumb: Int,
    val outline: Int,
)

object YSwitchPainter {
    @JvmStatic
    fun draw(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        geometry: YSwitchGeometry,
        checked: Boolean,
        enabled: Boolean,
        colors: YSwitchPaintColors,
    ) {
        val trackWidth = geometry.trackWidthDp * density
        val trackHeight = geometry.trackHeightDp * density
        val thumbDiameter = geometry.thumbDiameterDp * density
        val left = (widthPx - trackWidth) / 2f
        val top = (heightPx - trackHeight) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun tint(color: Int): Int {
            if (enabled) return color
            val alpha = (Color.alpha(color) * 0.38f).roundToInt().coerceIn(0, 255)
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }

        paint.color = tint(if (checked) colors.checkedTrack else colors.uncheckedTrack)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(left, top, left + trackWidth, top + trackHeight,
            trackHeight / 2f, trackHeight / 2f, paint)

        if (!checked) {
            val stroke = min(2f * geometry.scale * density, trackHeight / 4f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = tint(colors.outline)
            canvas.drawRoundRect(left + stroke / 2f, top + stroke / 2f,
                left + trackWidth - stroke / 2f, top + trackHeight - stroke / 2f,
                max(0f, (trackHeight - stroke) / 2f),
                max(0f, (trackHeight - stroke) / 2f), paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = tint(if (checked) colors.checkedThumb else colors.uncheckedThumb)
        val thumbRadius = thumbDiameter / 2f
        // One shared center-position formula; oversized thumbs stay centered.
        val inset = min(thumbRadius + 2f * density * geometry.scale, trackWidth / 2f)
        val x = left + if (checked) trackWidth - inset else inset
        canvas.drawCircle(x, heightPx / 2f, thumbRadius, paint)
    }
}

/**
 * Retains MaterialSwitch/CompoundButton callbacks and accessibility, but delegates
 * ALL pixels to the same YSwitchPainter used by Compose.
 */
class YUiMaterialSwitch(context: Context) : MaterialSwitch(context) {
    private val store = YAppearanceStore(context)
    private var appearance = store.appearance(YAppearanceStore.moduleIdFor(context))
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        post {
            appearance = store.appearance(YAppearanceStore.moduleIdFor(context))
            requestLayout()
            invalidate()
        }
    }

    init {
        showText = false
        text = ""
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        store.registerPreferenceListener(preferenceListener)
        appearance = store.appearance(YAppearanceStore.moduleIdFor(context))
    }

    override fun onDetachedFromWindow() {
        store.unregisterPreferenceListener(preferenceListener)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val geometry = appearance.switchGeometry()
        val density = resources.displayMetrics.density
        val width = (geometry.touchWidthDp * density).roundToInt().coerceAtLeast(1)
        val height = (geometry.touchHeightDp * density).roundToInt().coerceAtLeast(1)
        setMeasuredDimension(View.resolveSize(width, widthMeasureSpec),
            View.resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        // The MaterialSwitch's native track/thumb are deliberately not drawn.
        val c = context
        val geometry = appearance.switchGeometry()
        val checkedTrack = YView.accent(c)
        YSwitchPainter.draw(
            canvas, width.toFloat(), height.toFloat(), resources.displayMetrics.density,
            geometry, isChecked, isEnabled,
            YSwitchPaintColors(
                checkedTrack = checkedTrack,
                uncheckedTrack = YView.color(c,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest,
                    YView.surfaceContainer(c)),
                checkedThumb = YView.color(c,
                    com.google.android.material.R.attr.colorOnPrimary,
                    YView.surface(c)),
                uncheckedThumb = YView.onSurfaceVariant(c),
                outline = YView.outline(c),
            ),
        )
    }
}
