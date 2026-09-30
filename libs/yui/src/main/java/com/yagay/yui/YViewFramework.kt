package com.yagay.yui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.SwitchCompat

/** Java-friendly View counterpart to the Compose YFeature* framework. */
class YViewScreen internal constructor(
    val view: ScrollView,
    val content: LinearLayout,
)

enum class YViewStatusTone {
    Neutral,
    Good,
    Warning,
    Error,
}

object YViewLayout {
    private const val SCREEN_H = 16
    private const val SCREEN_V = 12
    private const val SECTION_GAP = 12
    private const val CONTROL_GAP = 8

    @JvmStatic
    @JvmOverloads
    fun install(
        activity: Activity,
        title: String,
        subtitle: String? = null,
    ): YViewScreen {
        val screen = screen(activity, title, subtitle)
        activity.setContentView(screen.view)
        return screen
    }

    @JvmStatic
    @JvmOverloads
    fun screen(
        context: Context,
        title: String,
        subtitle: String? = null,
    ): YViewScreen {
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            YView.applyRoot(this)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(context, SCREEN_H),
                dp(context, SCREEN_V),
                dp(context, SCREEN_H),
                dp(context, 24),
            )
        }
        scroll.addView(
            root,
            ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        header(root, title, subtitle)
        return YViewScreen(scroll, root)
    }

    @JvmStatic
    @JvmOverloads
    fun header(parent: LinearLayout, title: String, subtitle: String? = null) {
        val heading = TextView(parent.context).apply {
            text = title
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onSurface(parent.context))
        }
        parent.addView(heading, matchWrap())
        if (!subtitle.isNullOrBlank()) {
            parent.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    textSize = 13f
                    setTextColor(onSurfaceVariant(parent.context))
                    setPadding(0, dp(context, 2), 0, dp(context, SECTION_GAP))
                },
                matchWrap(),
            )
        } else {
            heading.setPadding(0, 0, 0, dp(parent.context, SECTION_GAP))
        }
    }

    @JvmStatic
    @JvmOverloads
    fun card(
        parent: LinearLayout,
        title: String,
        subtitle: String? = null,
    ): LinearLayout {
        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            YView.styleCard(this)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dp(parent.context, SECTION_GAP)
        }
        parent.addView(card, lp)

        card.addView(
            TextView(parent.context).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(onSurface(context))
            },
            matchWrap(),
        )
        if (!subtitle.isNullOrBlank()) {
            card.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    textSize = 12.5f
                    setTextColor(onSurfaceVariant(context))
                    setPadding(0, dp(context, 2), 0, dp(context, CONTROL_GAP))
                },
                matchWrap(),
            )
        }
        return card
    }

    @JvmStatic
    @JvmOverloads
    fun statusLine(
        context: Context,
        text: String,
        tone: YViewStatusTone = YViewStatusTone.Neutral,
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        setPadding(0, dp(context, 4), 0, dp(context, 4))
        setStatusTone(this, tone)
    }

    @JvmStatic
    @JvmOverloads
    fun setStatus(
        view: TextView,
        text: CharSequence,
        tone: YViewStatusTone = YViewStatusTone.Neutral,
    ) {
        view.text = text
        setStatusTone(view, tone)
    }

    @JvmStatic
    fun detailBlock(
        context: Context,
        title: String,
        description: String,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(context, 6), 0, dp(context, 6))
        addView(TextView(context).apply {
            text = title
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onSurface(context))
        })
        addView(TextView(context).apply {
            text = description
            textSize = 12.5f
            setTextColor(onSurfaceVariant(context))
            setPadding(0, dp(context, 2), 0, 0)
        })
    }

    @JvmStatic
    fun searchField(context: Context, hint: String): AppCompatEditText = AppCompatEditText(context).apply {
        this.hint = hint
        isSingleLine = true
        minHeight = dp(context, 48)
        setPadding(dp(context, 12), 0, dp(context, 12), 0)
        setTextColor(onSurface(context))
        setHintTextColor(onSurfaceVariant(context))
    }

    @JvmStatic
    fun primaryButton(context: Context, text: String): Button = Button(context).apply {
        this.text = text
        YView.stylePrimaryButton(this)
    }

    @JvmStatic
    fun secondaryButton(context: Context, text: String): Button = Button(context).apply {
        this.text = text
        YView.styleSecondaryButton(this)
    }

    @JvmStatic
    fun actionRow(parent: LinearLayout): LinearLayout {
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        parent.addView(row, matchWrap())
        return row
    }

    @JvmStatic
    @JvmOverloads
    fun addAction(row: LinearLayout, button: View, weight: Float = 1f) {
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        if (row.childCount > 0) lp.marginStart = dp(row.context, CONTROL_GAP)
        row.addView(button, lp)
    }

    @JvmStatic
    fun switchRow(
        parent: LinearLayout,
        title: String,
        description: String?,
        checked: Boolean,
        listener: CompoundButton.OnCheckedChangeListener,
    ): SwitchCompat {
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 6))
        }
        val texts = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
        }
        texts.addView(TextView(parent.context).apply {
            text = title
            textSize = 15f
            setTextColor(onSurface(context))
        })
        if (!description.isNullOrBlank()) {
            texts.addView(TextView(parent.context).apply {
                text = description
                textSize = 12.5f
                setTextColor(onSurfaceVariant(context))
                setPadding(0, dp(context, 2), dp(context, CONTROL_GAP), 0)
            })
        }
        row.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val toggle = SwitchCompat(parent.context).apply {
            isChecked = checked
            setOnCheckedChangeListener(listener)
        }
        row.addView(toggle)
        parent.addView(row, matchWrap())
        return toggle
    }

    @JvmStatic
    @JvmOverloads
    fun dividerSpace(parent: LinearLayout, spaceDp: Int = CONTROL_GAP) {
        parent.addView(
            View(parent.context),
            LinearLayout.LayoutParams(1, YView.dp(parent.context, spaceDp)),
        )
    }

    private fun setStatusTone(view: TextView, tone: YViewStatusTone) {
        view.setTextColor(
            when (tone) {
                YViewStatusTone.Neutral -> onSurfaceVariant(view.context)
                YViewStatusTone.Good -> YView.color(
                    view.context,
                    com.google.android.material.R.attr.colorPrimary,
                    0xFF16794A.toInt(),
                )
                YViewStatusTone.Warning -> YView.color(
                    view.context,
                    com.google.android.material.R.attr.colorTertiary,
                    0xFF9A6700.toInt(),
                )
                YViewStatusTone.Error -> YView.color(
                    view.context,
                    com.google.android.material.R.attr.colorError,
                    0xFFB3261E.toInt(),
                )
            },
        )
    }

    private fun onSurface(context: Context): Int = YView.color(
        context,
        com.google.android.material.R.attr.colorOnSurface,
        0xFF111318.toInt(),
    )

    private fun onSurfaceVariant(context: Context): Int = YView.color(
        context,
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        0xFF656A73.toInt(),
    )

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(context: Context, value: Int): Int = YView.dp(context, value)
}
