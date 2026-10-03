package com.yagay.yui

import android.app.Activity
import android.content.Context
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

class YViewScreen internal constructor(
    val view: ScrollView,
    val content: LinearLayout,
)

enum class YViewStatusTone { Neutral, Good, Warning, Error }

/** Java/View compatibility renderer backed by the generated YUI geometry resources. */
object YViewLayout {
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
            setPadding(screenH(context), screenV(context), screenH(context), 0)
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
            setPadding(screenH(context), screenV(context), screenH(context), dp(context, 28))
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
            YView.stylePageTitle(this)
        }
        parent.addView(heading, matchWrap())
        if (!subtitle.isNullOrBlank()) {
            parent.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    YView.styleBody(this)
                    setPadding(0, dp(context, 3), 0, sectionGap(context))
                },
                matchWrap(),
            )
        } else {
            heading.setPadding(0, 0, 0, sectionGap(parent.context))
        }
    }

    @JvmStatic
    @JvmOverloads
    fun sectionHeader(parent: LinearLayout, title: String, subtitle: String? = null): TextView {
        val heading = TextView(parent.context).apply {
            text = title
            YView.styleSectionTitle(this)
            setPadding(0, sectionGap(context), 0, dp(context, 4))
        }
        parent.addView(heading, matchWrap())
        if (!subtitle.isNullOrBlank()) {
            parent.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    YView.styleCaption(this)
                    setPadding(0, 0, 0, controlGap(context))
                },
                matchWrap(),
            )
        }
        return heading
    }

    @JvmStatic
    @JvmOverloads
    fun card(parent: LinearLayout, title: String, subtitle: String? = null): LinearLayout {
        val frame = MaterialCardView(parent.context).apply {
            radius = YView.dimen(context, R.dimen.yui_card_radius).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(YView.surfaceContainer(context))
        }
        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            val p = YView.dimen(context, R.dimen.yui_card_padding)
            setPadding(p, p, p, p)
        }
        frame.addView(
            card,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = sectionGap(parent.context)
        }
        parent.addView(frame, lp)
        card.addView(
            TextView(parent.context).apply {
                text = title
                YView.styleSectionTitle(this)
            },
            matchWrap(),
        )
        if (!subtitle.isNullOrBlank()) {
            card.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    YView.styleCaption(this)
                    setPadding(0, dp(context, 3), 0, controlGap(context))
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
            YView.styleBody(this)
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
        YView.styleBody(this)
        gravity = Gravity.CENTER
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
                YView.styleCaption(this)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.36f),
        )
        addView(
            TextView(context).apply {
                text = value
                YView.styleBody(this)
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
            YView.styleStrongBody(this)
        })
        addView(TextView(context).apply {
            text = description
            YView.styleCaption(this)
            setPadding(0, dp(context, 3), 0, 0)
        })
    }

    @JvmStatic
    @JvmOverloads
    fun listRow(context: Context, insetHorizontal: Boolean = false): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = YView.dimen(context, R.dimen.yui_touch_target)
        val horizontal = if (insetHorizontal) screenH(context) else 0
        val vertical = (controlGap(context) / 2).coerceAtLeast(1)
        setPadding(horizontal, vertical, horizontal, vertical)
    }

    @JvmStatic
    fun listTitle(context: Context): TextView = TextView(context).apply {
        YView.styleItemTitle(this)
        maxLines = 1
    }

    @JvmStatic
    fun listSubtitle(context: Context): TextView = TextView(context).apply {
        YView.styleCaption(this)
        maxLines = 2
    }

    @JvmStatic
    fun listGap(context: Context): Int = controlGap(context)

    @JvmStatic
    fun listIconSize(context: Context): Int = YView.dimen(context, R.dimen.yui_touch_target)

    @JvmStatic
    @JvmOverloads
    fun navigationRow(
        context: Context,
        title: String,
        description: String? = null,
        listener: View.OnClickListener,
    ): LinearLayout {
        val row = listRow(context).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener(listener)
        }
        val copy = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(listTitle(context).apply { text = title })
        if (!description.isNullOrBlank()) {
            copy.addView(listSubtitle(context).apply { text = description })
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            TextView(context).apply {
                text = "›"
                YView.styleSectionTitle(this)
                setTextColor(YView.onSurfaceVariant(context))
                gravity = Gravity.CENTER
                minimumWidth = YView.touchTarget(context)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return row
    }

    @JvmStatic
    @JvmOverloads
    fun navigationRow(
        parent: LinearLayout,
        title: String,
        description: String? = null,
        listener: View.OnClickListener,
    ): LinearLayout = navigationRow(parent.context, title, description, listener).also {
        parent.addView(it, matchWrap())
    }

    @JvmStatic
    fun searchField(context: Context, hint: String): AppCompatEditText = AppCompatEditText(context).apply {
        this.hint = hint
        isSingleLine = true
        minHeight = YView.dimen(context, R.dimen.yui_touch_target)
        setPadding(dp(context, 14), 0, dp(context, 14), 0)
        setTextColor(YView.onSurface(context))
        setHintTextColor(YView.onSurfaceVariant(context))
        background = YView.fieldBackground(context)
    }

    @JvmStatic fun primaryButton(context: Context, text: String): Button =
        MaterialButton(context).apply {
            this.text = text
            YView.stylePrimaryButton(this)
        }

    @JvmStatic fun secondaryButton(context: Context, text: String): Button =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
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
        if (row.childCount > 0) lp.marginStart = controlGap(row.context)
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
            minimumHeight = YView.dimen(context, R.dimen.yui_touch_target)
            setPadding(0, dp(context, 7), 0, dp(context, 7))
        }
        val texts = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(parent.context).apply {
            text = title
            YView.styleItemTitle(this)
        })
        if (!description.isNullOrBlank()) {
            texts.addView(TextView(parent.context).apply {
                text = description
                YView.styleCaption(this)
                setPadding(0, dp(context, 3), controlGap(context), 0)
            })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = MaterialSwitch(parent.context).apply {
            isChecked = checked
            setOnCheckedChangeListener(listener)
        }
        row.addView(toggle)
        parent.addView(row, matchWrap())
        return toggle
    }

    @JvmStatic
    fun dividerSpace(parent: LinearLayout, spaceDp: Int = 10) {
        parent.addView(View(parent.context), LinearLayout.LayoutParams(1, YView.dp(parent.context, spaceDp)))
    }

    private fun setStatusTone(view: TextView, tone: YViewStatusTone) {
        view.setTextColor(
            when (tone) {
                YViewStatusTone.Neutral -> YView.onSurfaceVariant(view.context)
                YViewStatusTone.Good -> YView.color(view.context, androidx.appcompat.R.attr.colorPrimary, 0xFF16794A.toInt())
                YViewStatusTone.Warning -> YView.color(view.context, com.google.android.material.R.attr.colorTertiary, 0xFF9A6700.toInt())
                YViewStatusTone.Error -> YView.color(view.context, android.R.attr.colorError, 0xFFB3261E.toInt())
            },
        )
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun screenH(context: Context): Int = YView.dimen(context, R.dimen.yui_screen_horizontal)
    private fun screenV(context: Context): Int = YView.dimen(context, R.dimen.yui_screen_vertical)
    private fun sectionGap(context: Context): Int = YView.dimen(context, R.dimen.yui_section_gap)
    private fun controlGap(context: Context): Int = YView.dimen(context, R.dimen.yui_control_gap)
    private fun dp(context: Context, value: Int): Int = YView.dp(context, value)
}
