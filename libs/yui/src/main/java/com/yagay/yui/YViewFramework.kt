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

class YViewScreen internal constructor(
    val view: ScrollView,
    val content: LinearLayout,
)

enum class YViewStatusTone { Neutral, Good, Warning, Error }

/** Java/View compatibility renderer backed by the same YUI 2.0 design language as Compose. */
object YViewLayout {
    private const val SCREEN_H = 18
    private const val SCREEN_V = 14
    private const val SECTION_GAP = 16
    private const val CONTROL_GAP = 10

    @JvmStatic
    @JvmOverloads
    fun install(activity: Activity, title: String, subtitle: String? = null): YViewScreen {
        val screen = screen(activity, title, subtitle)
        activity.setContentView(screen.view)
        return screen
    }

    @JvmStatic
    @JvmOverloads
    fun installFixed(activity: Activity, title: String, subtitle: String? = null): LinearLayout {
        val root = fixedScreen(activity, title, subtitle)
        activity.setContentView(root)
        return root
    }

    @JvmStatic
    @JvmOverloads
    fun fixedScreen(context: Context, title: String, subtitle: String? = null): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, SCREEN_H), dp(context, SCREEN_V), dp(context, SCREEN_H), 0)
            YView.applyRoot(this)
            header(this, title, subtitle)
        }

    @JvmStatic
    @JvmOverloads
    fun screen(context: Context, title: String, subtitle: String? = null): YViewScreen {
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
                dp(context, 28),
            )
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        header(root, title, subtitle)
        return YViewScreen(scroll, root)
    }

    @JvmStatic
    @JvmOverloads
    fun header(parent: LinearLayout, title: String, subtitle: String? = null) {
        val heading = TextView(parent.context).apply {
            text = title
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(YView.onSurface(context))
        }
        parent.addView(heading, matchWrap())
        if (!subtitle.isNullOrBlank()) {
            parent.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    textSize = 13f
                    setTextColor(YView.onSurfaceVariant(context))
                    setPadding(0, dp(context, 3), 0, dp(context, SECTION_GAP))
                },
                matchWrap(),
            )
        } else {
            heading.setPadding(0, 0, 0, dp(parent.context, SECTION_GAP))
        }
    }

    @JvmStatic
    @JvmOverloads
    fun sectionHeader(parent: LinearLayout, title: String, subtitle: String? = null): TextView {
        val heading = TextView(parent.context).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(YView.onSurface(context))
            setPadding(0, dp(context, SECTION_GAP), 0, dp(context, 4))
        }
        parent.addView(heading, matchWrap())
        if (!subtitle.isNullOrBlank()) {
            parent.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    textSize = 12.5f
                    setTextColor(YView.onSurfaceVariant(context))
                    setPadding(0, 0, 0, dp(context, CONTROL_GAP))
                },
                matchWrap(),
            )
        }
        return heading
    }

    @JvmStatic
    @JvmOverloads
    fun card(parent: LinearLayout, title: String, subtitle: String? = null): LinearLayout {
        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            YView.styleCard(this)
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(parent.context, SECTION_GAP)
        }
        parent.addView(card, lp)
        card.addView(
            TextView(parent.context).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(YView.onSurface(context))
            },
            matchWrap(),
        )
        if (!subtitle.isNullOrBlank()) {
            card.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    textSize = 12.5f
                    setTextColor(YView.onSurfaceVariant(context))
                    setPadding(0, dp(context, 3), 0, dp(context, CONTROL_GAP))
                },
                matchWrap(),
            )
        }
        return card
    }

    @JvmStatic
    @JvmOverloads
    fun statusLine(context: Context, text: String, tone: YViewStatusTone = YViewStatusTone.Neutral): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 14f
            setPadding(0, dp(context, 5), 0, dp(context, 5))
            setStatusTone(this, tone)
        }

    @JvmStatic
    @JvmOverloads
    fun setStatus(view: TextView, text: CharSequence, tone: YViewStatusTone = YViewStatusTone.Neutral) {
        view.text = text
        setStatusTone(view, tone)
    }

    @JvmStatic
    fun emptyState(context: Context, message: String): TextView = TextView(context).apply {
        text = message
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(YView.onSurfaceVariant(context))
        setPadding(0, dp(context, 22), 0, dp(context, 22))
    }

    @JvmStatic
    fun keyValueRow(context: Context, label: String, value: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        setPadding(0, dp(context, 5), 0, dp(context, 5))
        addView(
            TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(YView.onSurfaceVariant(context))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.36f),
        )
        addView(
            TextView(context).apply {
                text = value
                textSize = 13f
                setTextColor(YView.onSurface(context))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.64f),
        )
    }

    @JvmStatic
    fun detailBlock(context: Context, title: String, description: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(context, 7), 0, dp(context, 7))
        addView(TextView(context).apply {
            text = title
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(YView.onSurface(context))
        })
        addView(TextView(context).apply {
            text = description
            textSize = 12.5f
            setTextColor(YView.onSurfaceVariant(context))
            setPadding(0, dp(context, 3), 0, 0)
        })
    }

    @JvmStatic
    fun searchField(context: Context, hint: String): AppCompatEditText = AppCompatEditText(context).apply {
        this.hint = hint
        isSingleLine = true
        minHeight = dp(context, 48)
        setPadding(dp(context, 12), 0, dp(context, 12), 0)
        setTextColor(YView.onSurface(context))
        setHintTextColor(YView.onSurfaceVariant(context))
    }

    @JvmStatic fun primaryButton(context: Context, text: String): Button = Button(context).apply {
        this.text = text
        YView.stylePrimaryButton(this)
    }

    @JvmStatic fun secondaryButton(context: Context, text: String): Button = Button(context).apply {
        this.text = text
        YView.styleSecondaryButton(this)
    }

    @JvmStatic
    fun actionRow(parent: LinearLayout): LinearLayout {
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 4), 0, dp(context, 4))
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
            setPadding(0, dp(context, 7), 0, dp(context, 7))
        }
        val texts = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(parent.context).apply {
            text = title
            textSize = 15f
            setTextColor(YView.onSurface(context))
        })
        if (!description.isNullOrBlank()) {
            texts.addView(TextView(parent.context).apply {
                text = description
                textSize = 12.5f
                setTextColor(YView.onSurfaceVariant(context))
                setPadding(0, dp(context, 3), dp(context, CONTROL_GAP), 0)
            })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        parent.addView(View(parent.context), LinearLayout.LayoutParams(1, YView.dp(parent.context, spaceDp)))
    }

    private fun setStatusTone(view: TextView, tone: YViewStatusTone) {
        view.setTextColor(
            when (tone) {
                YViewStatusTone.Neutral -> YView.onSurfaceVariant(view.context)
                YViewStatusTone.Good -> YView.color(view.context, android.R.attr.colorAccent, 0xFF16794A.toInt())
                YViewStatusTone.Warning -> YView.color(view.context, com.google.android.material.R.attr.colorTertiary, 0xFF9A6700.toInt())
                YViewStatusTone.Error -> YView.color(view.context, android.R.attr.colorError, 0xFFB3261E.toInt())
            },
        )
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(context: Context, value: Int): Int = YView.dp(context, value)
}
