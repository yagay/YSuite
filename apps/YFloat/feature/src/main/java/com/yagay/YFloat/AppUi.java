package com.yagay.YFloat;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.yagay.yui.YView;
import com.yagay.yui.YViewLayout;

/**
 * Transitional Java/View adapter for YFloat normal settings screens.
 *
 * Layout geometry, window colors and ordinary controls come from shared YUI. YFloat keeps this
 * class only to avoid rewriting every Java call site at once; specialized floating OCR/result
 * overlays are intentionally outside this normal-screen adapter.
 */
final class AppUi {
    static final class Section {
        final MaterialCardView card;
        final LinearLayout body;
        Section(MaterialCardView card, LinearLayout body) { this.card = card; this.body = body; }
    }

    static LinearLayout pageRoot(Context c, String title, String subtitle) {
        return YViewLayout.fixedScreen(c, title, subtitle);
    }

    static ScrollView scrollPage(Context c, View content) {
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(background(c));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    static Section section(Context c, String title, String subtitle) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(surface(c));
        card.setRadius(YView.cardRadius(c));
        card.setStrokeWidth(0);
        card.setCardElevation(0);
        card.setUseCompatPadding(false);

        LinearLayout holder = new LinearLayout(c);
        holder.setOrientation(LinearLayout.VERTICAL);
        card.addView(holder, new MaterialCardView.LayoutParams(-1, -2));

        if ((title != null && !title.isBlank()) || (subtitle != null && !subtitle.isBlank())) {
            LinearLayout header = new LinearLayout(c);
            header.setOrientation(LinearLayout.VERTICAL);
            int cardPadding = YView.cardPadding(c);
            header.setPadding(cardPadding, cardPadding, cardPadding, YView.controlGap(c));
            if (title != null && !title.isBlank()) {
                TextView heading = new TextView(c);
                heading.setText(title);
                YView.styleSectionTitle(heading);
                header.addView(heading, new LinearLayout.LayoutParams(-1, -2));
            }
            if (subtitle != null && !subtitle.isBlank()) {
                TextView sub = caption(c, subtitle, 12.5f);
                sub.setPadding(0, Math.max(1, YView.controlGap(c) / 4), 0, 0);
                header.addView(sub, new LinearLayout.LayoutParams(-1, -2));
            }
            holder.addView(header, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, Math.max(1, YView.controlGap(c) / 3));
        holder.addView(body, new LinearLayout.LayoutParams(-1, -2));
        return new Section(card, body);
    }

    static void addSection(LinearLayout root, Section section) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = YView.sectionGap(root.getContext());
        root.addView(section.card, lp);
    }

    static void addRow(LinearLayout parent, View row) { parent.addView(row, new LinearLayout.LayoutParams(-1, -2)); }

    static View navRow(Context c, String title, String subtitle, Runnable action) {
        return YViewLayout.navigationRow(
                c,
                title,
                subtitle,
                v -> { if (action != null) action.run(); });
    }

    static SwitchMaterial switchRow(Context c, String title, String subtitle, boolean checked,
                                    android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = baseRow(c, subtitle == null || subtitle.isBlank());
        row.setBackground(rowBackground(c));
        LinearLayout copy = new LinearLayout(c);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(text(c, title, 15, false), new LinearLayout.LayoutParams(-1, -2));
        if (subtitle != null && !subtitle.isBlank()) {
            TextView sub = caption(c, subtitle, 12.5f);
            sub.setPadding(0, dp(c, 3), dp(c, 10), 0);
            copy.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        SwitchMaterial toggle = new SwitchMaterial(c);
        toggle.setUseMaterialThemeColors(true);
        toggle.setChecked(checked);
        toggle.setMinHeight(0);
        toggle.setMinimumHeight(0);
        if (listener != null) toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle, new LinearLayout.LayoutParams(-2, LinearLayout.LayoutParams.WRAP_CONTENT));
        return toggle;
    }

    static LinearLayout switchContainer(SwitchMaterial toggle) { return (LinearLayout) toggle.getParent(); }
    static LinearLayout baseRow(Context c) { return baseRow(c, false); }

    private static LinearLayout baseRow(Context c, boolean compact) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = YView.cardPadding(c);
        int vertical = compact ? 0 : Math.max(1, YView.controlGap(c) / 2);
        row.setPadding(horizontal, vertical, horizontal, vertical);
        row.setMinimumHeight(YView.touchTarget(c) + (compact ? 0 : Math.max(0, YView.controlGap(c) / 2)));
        return row;
    }

    static LinearLayout settingBlock(Context c) {
        LinearLayout block = new LinearLayout(c);
        block.setOrientation(LinearLayout.VERTICAL);
        int p = YView.cardPadding(c);
        block.setPadding(p, YView.controlGap(c), p, YView.controlGap(c));
        return block;
    }

    static LinearLayout sliderBlock(Context c) {
        LinearLayout block = new LinearLayout(c);
        block.setOrientation(LinearLayout.VERTICAL);
        int p = YView.cardPadding(c);
        block.setPadding(p, Math.max(1, YView.controlGap(c) / 2), p, Math.max(1, YView.controlGap(c) / 3));
        return block;
    }

    static LinearLayout buttonRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = YView.cardPadding(c);
        row.setPadding(p, Math.max(1, YView.controlGap(c) / 2), p, Math.max(1, YView.controlGap(c) / 2));
        return row;
    }

    static TextView text(Context c, String value, float sp, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value == null ? "" : value);
        if (bold) YView.styleStrongBody(tv);
        else YView.styleBody(tv);
        return tv;
    }

    static TextView caption(Context c, String value, float sp) {
        TextView tv = new TextView(c);
        tv.setText(value == null ? "" : value);
        YView.styleCaption(tv);
        tv.setLineSpacing(0f, 1.08f);
        return tv;
    }

    static TextView statusPill(Context c, String value, boolean positive) {
        TextView tv = text(c, value, 12, true);
        tv.setTextColor(positive ? success(c) : warning(c));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(YView.controlGap(c), Math.max(1, YView.controlGap(c) / 2),
                YView.controlGap(c), Math.max(1, YView.controlGap(c) / 2));
        tv.setBackground(rounded(c, positive ? successSurface(c) : warningSurface(c), 999));
        return tv;
    }

    static MaterialButton primaryButton(Context c, String value) {
        MaterialButton b = new MaterialButton(c);
        styleButton(c, b, value);
        return b;
    }

    static MaterialButton secondaryButton(Context c, String value) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        styleButton(c, b, value);
        return b;
    }

    static MaterialButton compactButton(Context c, String value) {
        MaterialButton b = secondaryButton(c, value);
        b.setMinHeight(YView.buttonHeight(c));
        b.setMinimumHeight(YView.buttonHeight(c));
        b.setMinimumWidth(0);
        b.setPadding(YView.controlGap(c), 0, YView.controlGap(c), 0);
        return b;
    }

    private static void styleButton(Context c, MaterialButton b, String value) {
        b.setText(value);
        YView.stylePrimaryButton(b);
    }

    static void styleInput(Context c, EditText input) {
        YView.styleBody(input);
        input.setTextColor(textPrimary(c));
        input.setHintTextColor(textSecondary(c));
        int p = YView.controlGap(c);
        input.setPadding(p, p, p, p);
        input.setBackground(YView.fieldBackground(c));
    }

    static android.graphics.drawable.Drawable rowBackground(Context c) {
        return new RippleDrawable(ColorStateList.valueOf(ripple(c)), new ColorDrawable(Color.TRANSPARENT), null);
    }

    static View divider(Context c) { View divider = new View(c); divider.setBackgroundColor(outline(c)); return divider; }

    static android.graphics.drawable.Drawable rounded(Context c, int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static int background(Context c) { return YView.background(c); }
    static int surface(Context c) { return YView.surfaceContainer(c); }
    static int surfaceAlt(Context c) { return YView.color(c, com.google.android.material.R.attr.colorSurfaceContainerHigh, surface(c)); }
    static int textPrimary(Context c) { return YView.onSurface(c); }
    static int textSecondary(Context c) { return YView.onSurfaceVariant(c); }
    static int outline(Context c) { return YView.outline(c); }
    static int success(Context c) { return YView.color(c, androidx.appcompat.R.attr.colorPrimary, YView.accent(c)); }
    static int warning(Context c) { return YView.color(c, com.google.android.material.R.attr.colorTertiary, YView.accent(c)); }
    static int successSurface(Context c) { return YView.color(c, com.google.android.material.R.attr.colorPrimaryContainer, surfaceAlt(c)); }
    static int warningSurface(Context c) { return YView.color(c, com.google.android.material.R.attr.colorTertiaryContainer, surfaceAlt(c)); }
    static int accent(Context c) { return YView.accent(c); }
    static int ripple(Context c) { return YView.color(c, android.R.attr.colorControlHighlight, 0x12000000); }
    static boolean dark(Context c) { return UiTokens.dark(c); }
    static int dp(Context c, int value) { return YView.dp(c, value); }

    private AppUi() {}
}
