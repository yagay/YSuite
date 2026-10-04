package com.yagay.yui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.materialswitch.MaterialSwitch

class YViewScreen internal constructor(
    val view: View,
    val content: LinearLayout,
)

enum class YViewStatusTone { Neutral, Good, Warning, Error }

class YViewSection(
    @JvmField val card: MaterialCardView,
    @JvmField val body: LinearLayout,
)

class YViewPage(
    @JvmField val root: LinearLayout,
    @JvmField val toolbar: MaterialToolbar,
    @JvmField val content: FrameLayout,
)

class YViewFilterBar(
    @JvmField val view: HorizontalScrollView,
    @JvmField val group: ChipGroup,
    @JvmField val chips: List<Chip>,
) {
    fun indexForId(id: Int): Int = chips.indexOfFirst { it.id == id }
}

/** Java/View compatibility renderer backed by the generated YUI geometry resources. */
object YViewLayout {

    /** Canonical View-system page shell for all normal Java/XML-era feature screens. */
    @JvmStatic
    @JvmOverloads
    fun installPage(activity: Activity, title: String, subtitle: String? = null): YViewPage {
        val page = page(activity, title, subtitle)
        activity.setContentView(page.root)
        return page
    }

    @JvmStatic
    @JvmOverloads
    fun page(context: Context, title: String, subtitle: String? = null): YViewPage {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            YView.applyRoot(this)
        }
        val toolbar = MaterialToolbar(context).apply {
            this.title = title
            this.subtitle = subtitle
            setTitleTextAppearance(context, R.style.TextAppearance_YUI_PageTitle)
            setTitleTextColor(YView.onSurface(context))
            setSubtitleTextColor(YView.onSurfaceVariant(context))
            setContentInsetsRelative(
                YView.screenHorizontal(context),
                YView.screenHorizontal(context),
            )
            minimumHeight = YView.dimen(context, R.dimen.yui_toolbar_height)
            elevation = 0f
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val content = FrameLayout(context).apply {
            clipToPadding = false
        }
        root.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return YViewPage(root, toolbar, content)
    }

    @JvmStatic
    fun filterBar(context: Context, labels: List<String>, selectedIndex: Int = 0): YViewFilterBar {
        val group = ChipGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(
                YView.screenHorizontal(context),
                YView.controlGap(context) / 2,
                YView.screenHorizontal(context),
                YView.controlGap(context) / 2,
            )
        }
        val chips = labels.mapIndexed { index, label ->
            Chip(context).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isChecked = index == selectedIndex
                group.addView(this)
            }
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                group,
                HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        return YViewFilterBar(scroll, group, chips)
    }

    @JvmStatic
    fun bottomNavigation(context: Context, @MenuRes menuRes: Int): BottomNavigationView =
        BottomNavigationView(context).apply {
            inflateMenu(menuRes)
            elevation = 0f
            setBackgroundColor(YView.background(context))
        }

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
        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            YView.applyRoot(this)
        }
        val toolbar = MaterialToolbar(context).apply {
            this.title = title
            this.subtitle = subtitle
            setBackgroundColor(YView.surface(context))
            setTitleTextColor(YView.onSurface(context))
            setSubtitleTextColor(YView.onSurfaceVariant(context))
            setContentInsetsRelative(screenH(context), screenH(context))
            minimumHeight = YView.dimen(context, R.dimen.yui_toolbar_height)
        }
        shell.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            YView.applyRoot(this)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(screenH(context), screenV(context), screenH(context), dp(context, 28))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        shell.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return YViewScreen(shell, root)
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
                    YView.styleCaption(this)
                    setPadding(0, Math.max(1, controlGap(context) / 4), 0, controlGap(context))
                },
                matchWrap(),
            )
        } else {
            heading.setPadding(0, 0, 0, controlGap(parent.context))
        }

        parent.addView(
            View(parent.context).apply { setBackgroundColor(YView.outline(context)) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(parent.context, 1),
            ).apply {
                bottomMargin = sectionGap(parent.context)
            },
        )
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
        val section = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
        }
        parent.addView(
            section,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        section.addView(
            TextView(parent.context).apply {
                text = title
                YView.styleSectionTitle(this)
            },
            matchWrap(),
        )
        if (!subtitle.isNullOrBlank()) {
            section.addView(
                TextView(parent.context).apply {
                    text = subtitle
                    YView.styleCaption(this)
                    setPadding(0, Math.max(1, controlGap(context) / 4), 0, controlGap(context))
                },
                matchWrap(),
            )
        }
        parent.addView(
            View(parent.context).apply { setBackgroundColor(YView.outline(context)) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(parent.context, 1),
            ).apply {
                topMargin = controlGap(parent.context)
                bottomMargin = sectionGap(parent.context)
            },
        )
        return section
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

    @JvmStatic fun primaryButton(context: Context, text: String): MaterialButton =
        MaterialButton(context).apply {
            this.text = text
            YView.stylePrimaryButton(this)
        }

    @JvmStatic fun secondaryButton(context: Context, text: String): MaterialButton =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            YView.styleSecondaryButton(this)
        }

    /** Unified normal-screen View helpers. Feature modules must not own another UI utility layer. */
    @JvmStatic
    @JvmOverloads
    fun pageRoot(context: Context, title: String, subtitle: String? = null): LinearLayout =
        fixedScreen(context, title, subtitle)

    @JvmStatic
    fun scrollPage(context: Context, content: View): ScrollView = ScrollView(context).apply {
        isFillViewport = true
        clipToPadding = false
        setBackgroundColor(YView.background(context))
        addView(content, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    @JvmStatic
    @JvmOverloads
    fun section(context: Context, title: String? = null, subtitle: String? = null): YViewSection {
        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            radius = 0f
            strokeWidth = 0
            cardElevation = 0f
            useCompatPadding = false
        }
        val holder = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        card.addView(holder, MaterialCardView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!title.isNullOrBlank() || !subtitle.isNullOrBlank()) {
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val p = YView.cardPadding(context)
                setPadding(p, p, p, YView.controlGap(context))
            }
            if (!title.isNullOrBlank()) {
                header.addView(TextView(context).apply {
                    text = title
                    YView.styleSectionTitle(this)
                }, matchWrap())
            }
            if (!subtitle.isNullOrBlank()) {
                header.addView(caption(context, subtitle, 12.5f).apply {
                    setPadding(0, maxOf(1, YView.controlGap(context) / 4), 0, 0)
                }, matchWrap())
            }
            holder.addView(header, matchWrap())
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, maxOf(1, YView.controlGap(context) / 3))
        }
        holder.addView(body, matchWrap())
        return YViewSection(card, body)
    }

    @JvmStatic
    fun addSection(root: LinearLayout, section: YViewSection) {
        root.addView(section.card, matchWrap())
        root.addView(
            divider(root.context),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxOf(1, dp(root.context, 1)),
            ).apply {
                topMargin = YView.controlGap(root.context)
                bottomMargin = YView.sectionGap(root.context)
            },
        )
    }

    @JvmStatic
    fun addRow(parent: LinearLayout, row: View) {
        parent.addView(row, matchWrap())
    }

    @JvmStatic
    fun navRow(context: Context, title: String, subtitle: String?, action: Runnable?): View =
        navigationRow(context, title, subtitle) { action?.run() }

    @JvmStatic
    fun switchRow(
        context: Context,
        title: String,
        subtitle: String?,
        checked: Boolean,
        listener: CompoundButton.OnCheckedChangeListener?,
    ): SwitchMaterial {
        val row = baseRow(context, subtitle.isNullOrBlank()).apply { background = rowBackground(context) }
        val copy = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(context, title, 15f, false), matchWrap())
            if (!subtitle.isNullOrBlank()) {
                addView(caption(context, subtitle, 12.5f).apply {
                    setPadding(0, dp(context, 3), dp(context, 10), 0)
                }, matchWrap())
            }
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return SwitchMaterial(context).apply {
            isUseMaterialThemeColors = true
            isChecked = checked
            minHeight = 0
            minimumHeight = 0
            if (listener != null) setOnCheckedChangeListener(listener)
            row.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    @JvmStatic
    fun switchContainer(toggle: SwitchMaterial): LinearLayout = toggle.parent as LinearLayout

    @JvmStatic
    fun baseRow(context: Context): LinearLayout = baseRow(context, false)

    private fun baseRow(context: Context, compact: Boolean): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val horizontal = YView.cardPadding(context)
        val vertical = if (compact) 0 else maxOf(1, YView.controlGap(context) / 2)
        setPadding(horizontal, vertical, horizontal, vertical)
        minimumHeight = YView.touchTarget(context) + if (compact) 0 else maxOf(0, YView.controlGap(context) / 2)
    }

    @JvmStatic
    fun settingBlock(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = YView.cardPadding(context)
        setPadding(p, YView.controlGap(context), p, YView.controlGap(context))
    }

    @JvmStatic
    fun sliderBlock(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = YView.cardPadding(context)
        setPadding(p, maxOf(1, YView.controlGap(context) / 2), p, maxOf(1, YView.controlGap(context) / 3))
    }

    @JvmStatic
    fun buttonRow(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = YView.cardPadding(context)
        setPadding(p, maxOf(1, YView.controlGap(context) / 2), p, maxOf(1, YView.controlGap(context) / 2))
    }

    @JvmStatic
    fun text(context: Context, value: String?, sp: Float, bold: Boolean): TextView = TextView(context).apply {
        text = value.orEmpty()
        if (bold) YView.styleStrongBody(this) else YView.styleBody(this)
    }

    @JvmStatic
    fun caption(context: Context, value: String?, sp: Float): TextView = TextView(context).apply {
        text = value.orEmpty()
        YView.styleCaption(this)
        setLineSpacing(0f, 1.08f)
    }

    @JvmStatic
    fun statusPill(context: Context, value: String?, positive: Boolean): TextView =
        text(context, value, 12f, true).apply {
            setTextColor(if (positive) success(context) else warning(context))
            gravity = Gravity.CENTER
            setPadding(
                YView.controlGap(context),
                maxOf(1, YView.controlGap(context) / 2),
                YView.controlGap(context),
                maxOf(1, YView.controlGap(context) / 2),
            )
            background = rounded(context, if (positive) successSurface(context) else warningSurface(context), 999)
        }

    @JvmStatic
    fun compactButton(context: Context, value: String): MaterialButton = secondaryButton(context, value).apply {
        minHeight = YView.buttonHeight(context)
        minimumHeight = YView.buttonHeight(context)
        minimumWidth = 0
        setPadding(YView.controlGap(context), 0, YView.controlGap(context), 0)
    }

    @JvmStatic
    fun styleInput(context: Context, input: EditText) {
        YView.styleBody(input)
        input.setTextColor(textPrimary(context))
        input.setHintTextColor(textSecondary(context))
        val p = YView.controlGap(context)
        input.setPadding(p, p, p, p)
        input.background = YView.fieldBackground(context)
    }

    @JvmStatic
    fun rowBackground(context: Context) = RippleDrawable(
        ColorStateList.valueOf(YView.color(context, android.R.attr.colorControlHighlight, 0x12000000)),
        ColorDrawable(Color.TRANSPARENT),
        null,
    )

    @JvmStatic
    fun divider(context: Context): View = View(context).apply { setBackgroundColor(YView.outline(context)) }

    @JvmStatic
    fun rounded(context: Context, color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(context, radiusDp).toFloat()
    }

    @JvmStatic fun textPrimary(context: Context): Int = YView.onSurface(context)
    @JvmStatic fun textSecondary(context: Context): Int = YView.onSurfaceVariant(context)
    @JvmStatic fun success(context: Context): Int = YView.success(context)
    @JvmStatic fun warning(context: Context): Int = YView.warning(context)
    @JvmStatic fun successSurface(context: Context): Int = YView.successContainer(context)
    @JvmStatic fun warningSurface(context: Context): Int = YView.warningContainer(context)

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
    @JvmStatic fun dp(context: Context, value: Int): Int = YView.dp(context, value)
}
