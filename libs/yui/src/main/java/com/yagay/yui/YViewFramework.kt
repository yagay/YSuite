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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.slider.Slider
import java.util.function.IntConsumer
import java.util.function.IntFunction

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

class YViewListRow(
    @JvmField val root: LinearLayout,
    @JvmField val copy: LinearLayout,
    @JvmField val title: TextView,
    @JvmField val subtitle: TextView,
    @JvmField val icon: ImageView?,
    @JvmField val trailing: LinearLayout,
)

class YViewRadioGroup(
    @JvmField val group: RadioGroup,
    @JvmField val buttons: List<RadioButton>,
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
            minimumHeight = YView.toolbarHeight(context)
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
                FrameLayout.LayoutParams(
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
            setPadding(screenH(context), screenV(context), screenH(context), sectionGap(context))
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
            minimumHeight = YView.toolbarHeight(context)
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

    /**
     * Legacy Java facade backed by the exact same MaterialCardView used by
     * section().  Returned LinearLayout stays source/binary compatible with the
     * old callers, but backgrounds, radius, headers and dividers are not separate.
     */
    @JvmStatic
    @JvmOverloads
    fun card(parent: LinearLayout, title: String, subtitle: String? = null): LinearLayout {
        val unified = section(parent.context, title, subtitle)
        addSection(parent, unified)
        val content = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = YView.cardPadding(context)
            setPadding(horizontal, 0, horizontal, 0)
        }
        unified.body.addView(content, matchWrap())
        return content
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
        minimumHeight = YView.rowHeight(context)
        val horizontal = if (insetHorizontal) YView.rowHorizontalPadding(context) else 0
        val vertical = YView.rowVerticalPadding(context)
        setPadding(horizontal, vertical, horizontal, vertical)
    }

    @JvmStatic
    fun listTitle(context: Context): TextView = TextView(context).apply {
        YView.styleItemTitle(this)
        maxLines = 1
    }

    /** Canonical title for a selectable row in all Java/View feature screens. */
    @JvmStatic
    fun rowTitle(context: Context, value: String?): TextView = TextView(context).apply {
        text = value.orEmpty()
        YView.styleItemTitle(this)
        isSingleLine = false
        maxLines = Int.MAX_VALUE
        ellipsize = null
    }

    /**
     * Canonical description beneath a row title. Typography, line height and
     * spacing come from YUI, including module-specific appearance settings.
     */
    @JvmStatic
    fun rowSubtitle(context: Context, value: String?): TextView =
        caption(context, value, 12.5f).apply {
            gravity = Gravity.START
            isSingleLine = false
            maxLines = Int.MAX_VALUE
            ellipsize = null
            setPadding(0, maxOf(1, YView.controlGap(context) / 4),
                YView.controlGap(context), 0)
        }

    @JvmStatic
    fun listSubtitle(context: Context): TextView = rowSubtitle(context, null)

    @JvmStatic
    fun listGap(context: Context): Int = controlGap(context)

    @JvmStatic
    fun listIconSize(context: Context): Int = YView.listIconSize(context)

    @JvmStatic
    @JvmOverloads
    fun listItem(context: Context, withIcon: Boolean = false): YViewListRow {
        val row = listRow(context)
        val icon = if (withIcon) {
            ImageView(context).also {
                val size = listIconSize(context)
                row.addView(it, LinearLayout.LayoutParams(size, size))
            }
        } else null
        val copy = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = listTitle(context)
        val subtitle = listSubtitle(context)
        copy.addView(title)
        copy.addView(subtitle)
        row.addView(
            copy,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = if (withIcon) listGap(context) else 0
            },
        )
        val trailing = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            trailing,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return YViewListRow(row, copy, title, subtitle, icon, trailing)
    }

    @JvmStatic
    fun checkBoxControl(
        context: Context,
        checked: Boolean,
        listener: CompoundButton.OnCheckedChangeListener?,
    ): MaterialCheckBox = MaterialCheckBox(context).apply {
        isUseMaterialThemeColors = true
        isChecked = checked
        minimumHeight = YView.touchTarget(context)
        if (listener != null) setOnCheckedChangeListener(listener)
    }

    @JvmStatic
    fun progressIndicator(context: Context): ProgressBar = ProgressBar(context).apply {
        isIndeterminate = true
    }

    @JvmStatic
    fun listView(context: Context): ListView = ListView(context).apply {
        dividerHeight = maxOf(1, dp(context, 1))
        setBackgroundColor(Color.TRANSPARENT)
    }

    @JvmStatic
    @JvmOverloads
    fun contentColumn(context: Context, padded: Boolean = true): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (padded) {
            setPadding(
                YView.screenHorizontal(context),
                YView.screenVertical(context),
                YView.screenHorizontal(context),
                YView.sectionGap(context),
            )
        }
    }

    @JvmStatic
    fun radioGroup(context: Context, labels: Array<String>): YViewRadioGroup {
        val group = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        val buttons = labels.map { label ->
            RadioButton(context).apply {
                id = View.generateViewId()
                text = label
                minimumHeight = YView.touchTarget(context)
                setTextColor(YView.onSurface(context))
                group.addView(
                    this,
                    RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }
        return YViewRadioGroup(group, buttons)
    }

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
        copy.addView(rowTitle(context, title))
        if (!description.isNullOrBlank()) {
            copy.addView(rowSubtitle(context, description))
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            TextView(context).apply {
                text = "›"
                YView.styleSectionTitle(this)
                setTextColor(YView.onSurfaceVariant(context))
                gravity = Gravity.CENTER
                minimumWidth = dp(context, 28)
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
        minHeight = YView.touchTarget(context)
        setPadding(dp(context, 14), 0, dp(context, 14), 0)
        setTextColor(YView.onSurface(context))
        setHintTextColor(YView.onSurfaceVariant(context))
        background = YView.fieldBackground(context)
    }

    @JvmStatic
    @JvmOverloads
    fun textInput(
        context: Context,
        hint: String? = null,
        value: String? = null,
        singleLine: Boolean = true,
    ): AppCompatEditText = AppCompatEditText(context).apply {
        this.hint = hint
        setText(value.orEmpty())
        isSingleLine = singleLine
        minHeight = YView.touchTarget(context)
        styleInput(context, this)
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
        if (content is LinearLayout) {
            content.setPadding(
                content.paddingLeft, content.paddingTop, content.paddingRight,
                maxOf(content.paddingBottom, sectionGap(context) + YView.controlGap(context)),
            )
        }
        isFillViewport = true
        clipToPadding = false
        setBackgroundColor(YView.background(context))
        addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    @JvmStatic
    @JvmOverloads
    fun section(context: Context, title: String? = null, subtitle: String? = null): YViewSection {
        val card = MaterialCardView(context).apply {
            // Use the same Material 3 card surface, radius and elevation as Compose pages.
            setCardBackgroundColor(YView.surfaceContainer(context))
            radius = YView.cardRadius(context).toFloat()
            strokeWidth = 0
            cardElevation = 0f
            useCompatPadding = false
        }
        val holder = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        card.addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
            setPadding(0, 0, 0, maxOf(YView.cardPadding(context), YView.controlGap(context)))
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
                topMargin = maxOf(2, YView.controlGap(root.context) / 2)
                bottomMargin = maxOf(2, YView.controlGap(root.context) / 2)
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

    /**
     * One M3 MaterialSwitch renderer for *both* legacy View overloads.
     * Checked/unchecked colors use the same YUI appearance as Compose Switch.
     */
    private fun styleStandardToggle(toggle: SwitchCompat) {
        toggle.showText = false
        toggle.scaleX = 1f
        toggle.scaleY = 1f
        val c = toggle.context
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        toggle.thumbTintList = ColorStateList(
            states, intArrayOf(YView.surface(c), YView.onSurfaceVariant(c)),
        )
        toggle.trackTintList = ColorStateList(
            states, intArrayOf(YView.accent(c), YView.surfaceContainer(c)),
        )
    }

    private fun switchSlot(context: Context): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            YView.switchSlotWidth(context),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

    @JvmStatic
    fun switchRow(
        context: Context,
        title: String,
        subtitle: String?,
        checked: Boolean,
        listener: CompoundButton.OnCheckedChangeListener?,
    ): MaterialSwitch {
        val row = baseRow(context, subtitle.isNullOrBlank()).apply { background = rowBackground(context) }
        val copy = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(rowTitle(context, title), matchWrap())
            if (!subtitle.isNullOrBlank()) {
                addView(rowSubtitle(context, subtitle), matchWrap())
            }
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return MaterialSwitch(context).apply {
            isChecked = checked
            styleStandardToggle(this)
            if (listener != null) setOnCheckedChangeListener(listener)
            row.addView(this, switchSlot(context))
            row.setOnClickListener { performClick() }
        }
    }

    @JvmStatic
    fun switchContainer(toggle: MaterialSwitch): LinearLayout = toggle.parent as LinearLayout

    @JvmStatic
    fun baseRow(context: Context): LinearLayout = baseRow(context, false)

    private fun baseRow(context: Context, compact: Boolean): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val horizontal = YView.cardPadding(context)
        val vertical = maxOf(1, YView.controlGap(context) / 2)
        setPadding(horizontal, vertical, horizontal, vertical)
        minimumHeight = YView.rowHeight(context)
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
        // Legacy callers explicitly supply their content hierarchy in sp.
        // Apply it *after* the common TextAppearance, otherwise the style overrides it.
        if (sp > 0f) setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp * YView.fontPercent(context) / 100f)
    }

    @JvmStatic
    fun caption(context: Context, value: String?, sp: Float): TextView = TextView(context).apply {
        text = value.orEmpty()
        YView.styleCaption(this)
        if (sp > 0f) setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp * YView.fontPercent(context) / 100f)
        setLineSpacing(0f, 1.08f)
    }

    /**
     * Canonical explanation row inside a YUI section card. The card header and
     * its footnote share one horizontal inset, font role and dynamic text height.
     * Keep module screens from hand-placing captions flush against card borders.
     */
    @JvmStatic
    fun sectionNote(context: Context, value: String?): TextView =
        caption(context, value, 12.5f).apply {
            gravity = Gravity.START
            isSingleLine = false
            maxLines = Int.MAX_VALUE
            ellipsize = null
            val horizontal = YView.cardPadding(context)
            val vertical = maxOf(1, YView.rowVerticalPadding(context))
            setPadding(horizontal, vertical, horizontal, vertical)
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

    /** Kept for Java call sites: this now uses the same standard size as secondaryButton. */
    @Deprecated("Use secondaryButton; compact button density has been removed.")
    @JvmStatic
    fun compactButton(context: Context, value: String): MaterialButton = secondaryButton(context, value)

    @JvmStatic
    @JvmOverloads
    fun spinnerSetting(
        context: Context,
        title: String,
        labels: Array<String>,
        selected: Int,
        vertical: Boolean = false,
        onSelected: IntConsumer? = null,
    ): View {
        val spinner = spinnerControl(context, labels, selected, onSelected)
        val block = settingBlock(context)
        if (vertical) {
            block.addView(text(context, title, 14f, false))
            block.addView(
                spinner,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = maxOf(1, YView.controlGap(context) / 4) },
            )
        } else {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                text(context, title, 14f, false),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.82f),
            )
            row.addView(
                spinner,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.18f),
            )
            block.addView(row, matchWrap())
        }
        return block
    }

    @JvmStatic
    @JvmOverloads
    fun spinnerControl(
        context: Context,
        labels: Array<String>,
        selected: Int = 0,
        onSelected: IntConsumer? = null,
    ): Spinner {
        val safe = labels.ifEmpty { arrayOf("") }
        return Spinner(context).apply {
            minimumHeight = YView.touchTarget(context)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, safe)
            setSelection(selected.coerceIn(0, safe.lastIndex), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position in safe.indices) onSelected?.accept(position)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    @JvmStatic
    fun sliderSetting(
        context: Context,
        title: String,
        min: Int,
        max: Int,
        current: Int,
        valueLabel: IntFunction<String>?,
        onChanged: IntConsumer?,
    ): View {
        val block = sliderBlock(context)
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = text(context, title, 14f, false)
        val initial = current.coerceIn(min, max)
        val value = caption(context, valueLabel?.apply(initial) ?: initial.toString(), 13f).apply {
            gravity = Gravity.END
        }
        top.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        block.addView(top, matchWrap())

        val precise = AppCompatEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setText(initial.toString())
            hint = context.getString(R.string.yui_allowed_range, min, max)
            setTextColor(YView.onSurface(context))
            background = YView.fieldBackground(context)
            setPadding(YView.controlGap(context), YView.rowVerticalPadding(context),
                YView.controlGap(context), YView.rowVerticalPadding(context))
            setSelectAllOnFocus(true)
        }
        val slider = Slider(context).apply {
            valueFrom = min.toFloat()
            valueTo = max.toFloat()
            stepSize = 1f
            this.value = initial.toFloat()
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            addOnChangeListener { _, next, fromUser ->
                if (!fromUser) return@addOnChangeListener
                val intValue = next.toInt().coerceIn(min, max)
                value.text = valueLabel?.apply(intValue) ?: intValue.toString()
                precise.setText(intValue.toString())
                onChanged?.accept(intValue)
            }
        }
        block.addView(
            slider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        precise.setOnEditorActionListener { _, _, _ ->
            val entered = precise.text?.toString()?.toIntOrNull()
            if (entered != null && entered in min..max) {
                slider.value = entered.toFloat()
                value.text = valueLabel?.apply(entered) ?: entered.toString()
                onChanged?.accept(entered)
                precise.error = null
            } else {
                precise.error = context.getString(R.string.yui_allowed_range, min, max)
            }
            true
        }
        block.addView(precise, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        return block
    }

    @JvmStatic
    fun checkBoxRow(
        context: Context,
        title: String,
        checked: Boolean,
        listener: CompoundButton.OnCheckedChangeListener?,
    ): MaterialCheckBox = MaterialCheckBox(context).apply {
        isUseMaterialThemeColors = true
        text = title
        setTextColor(textPrimary(context))
        textSize = 14f * YView.fontPercent(context) / 100f
        val horizontal = YView.controlGap(context)
        val vertical = maxOf(1, YView.controlGap(context) / 2)
        setPadding(horizontal, vertical, horizontal, vertical)
        isChecked = checked
        if (listener != null) setOnCheckedChangeListener(listener)
        minimumHeight = YView.touchTarget(context)
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
        // No separate parent-based renderer: identical shape, typography, spacing,
        // colors, and click behavior to switchRow(Context, ...).
        val toggle = switchRow(parent.context, title, description, checked, listener)
        val row = switchContainer(toggle)
        // Parent-based rows inherit the parent's existing content inset. The
        // context-based overload provides its own inset within section.body.
        row.setPadding(0, row.paddingTop, 0, row.paddingBottom)
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
                YViewStatusTone.Warning -> YView.warning(view.context)
                YViewStatusTone.Error -> YView.color(view.context, android.R.attr.colorError, 0xFFB3261E.toInt())
            },
        )
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun screenH(context: Context): Int = YView.screenHorizontal(context)
    private fun screenV(context: Context): Int = YView.dimen(context, R.dimen.yui_screen_vertical)
    private fun sectionGap(context: Context): Int = YView.sectionGap(context)
    private fun controlGap(context: Context): Int = YView.controlGap(context)
    @JvmStatic fun dp(context: Context, value: Int): Int = YView.dp(context, value)
}
