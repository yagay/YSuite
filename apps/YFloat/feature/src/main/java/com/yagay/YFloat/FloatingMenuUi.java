package com.yagay.YFloat;

import com.yagay.yui.YOverlayTokens;
import com.yagay.yui.YOverlayUi;
import com.yagay.yui.YView;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.view.View;
import android.view.ViewGroup;
import com.yagay.yui.YUiGeometry;
import java.util.List;
import android.widget.TextView;

/** Single visual factory for YFloat floating text/image action menus. */
final class FloatingMenuUi {
    static LinearLayout root(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(YOverlayUi.menuBackground(c, "yfloat",
                YOverlayTokens.menuSurface(YOverlayUi.scopedContext(c, "yfloat"))));
        root.setElevation(YOverlayUi.elevationPx(c, "yfloat"));
        root.setClipToOutline(false);
        root.setClickable(true);
        return root;
    }

    static TextView action(Context c, String text) {
        TextView tv = baseText(c, text);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(YView.touchTarget(YOverlayUi.scopedContext(c, "yfloat")));
        tv.setSingleLine(false);
        tv.setMaxLines(Integer.MAX_VALUE);
        tv.setEllipsize(null);
        tv.setPadding(YOverlayUi.contentPaddingPx(c, "yfloat"),
                Math.max(1, YView.controlGap(c) / 2),
                YOverlayUi.contentPaddingPx(c, "yfloat"),
                Math.max(1, YView.controlGap(c) / 2));
        return tv;
    }

    static TextView row(Context c, String text, Drawable icon) {
        TextView tv = baseText(c, text);
        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tv.setPadding(YOverlayUi.contentPaddingPx(c, "yfloat"),
                Math.max(1, YView.controlGap(c) / 2),
                YOverlayUi.contentPaddingPx(c, "yfloat"),
                Math.max(1, YView.controlGap(c) / 2));
        tv.setSingleLine(false);
        tv.setMaxLines(Integer.MAX_VALUE);
        tv.setEllipsize(null);
        if (icon != null) {
            int s = YView.iconSize(c);
            icon.setBounds(0, 0, s, s);
            tv.setCompoundDrawablePadding(YView.controlGap(c));
            tv.setCompoundDrawables(icon, null, null, null);
        }
        return tv;
    }

    /**
     * Adaptive, discoverable action grid shared by text and image menus.
     * Prefer three actions across when their full labels fit, otherwise two.
     * Keep YUI typography, touch heights, ripple and corner geometry owned
     * by action(); do not create an independent set of button styles.
     */
    static GridLayout actionGrid(Context context, List<? extends View> actions,
                                 int outsidePadding) {
        GridLayout grid = new GridLayout(context);
        grid.setOrientation(GridLayout.HORIZONTAL);
        if (actions == null || actions.isEmpty()) return grid;

        int screenWidth = ScreenGeometry.usableBounds(context).width();
        int screenInset = YUiGeometry.overlayMenuInsetPx(context, "yfloat");
        int cap = Math.max(YView.touchTarget(context),
                screenWidth - screenInset * 2 - Math.max(0,outsidePadding));
        int minCell = YView.touchTarget(YOverlayUi.scopedContext(context, "yfloat"));
        int largest = minCell;
        for (View action : actions) {
            if (action instanceof TextView text) {
                CharSequence label = text.getText();
                int textWidth = label == null ? 0 :
                        (int)Math.ceil(text.getPaint().measureText(label.toString()));
                largest = Math.max(largest, textWidth +
                        text.getPaddingLeft() + text.getPaddingRight());
            }
        }
        int columns = gridColumns(cap, minCell, largest, actions.size());
        int cellWidth = Math.max(minCell, Math.min(largest, cap / columns));
        int contentWidth = Math.min(cap, cellWidth * columns);
        grid.setColumnCount(columns);
        grid.setRowCount((actions.size() + columns - 1) / columns);
        grid.setLayoutParams(new LinearLayout.LayoutParams(contentWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int gap = Math.max(0, YView.controlGap(context) / 4);
        for (int i=0;i<actions.size();i++) {
            View action=actions.get(i);
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(
                    GridLayout.spec(i / columns),
                    GridLayout.spec(i % columns,1f));
            p.width=0;
            p.height=ViewGroup.LayoutParams.WRAP_CONTENT;
            p.setGravity(Gravity.FILL);
            p.setMargins(gap,gap,gap,gap);
            if (action instanceof TextView label) {
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(false);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            grid.addView(action,p);
        }
        return grid;
    }

    /** Pure layout decision: never force the entire menu into a vertical stack. */
    static int gridColumns(int availableWidth,int minCell,int preferredCell,int itemCount) {
        if(itemCount<=1) return 1;
        if(availableWidth < Math.max(1,minCell)*2) return 1;
        int maxColumns=Math.min(3,itemCount);
        for(int columns=maxColumns;columns>=2;columns--) {
            if(availableWidth>=Math.max(1,preferredCell)*columns)
                return columns;
        }
        // Long text may wrap to two lines; retain side-by-side actions.
        return 2;
    }

    static TextView secondaryRow(Context c, String text) {
        TextView tv = row(c, text, null);
        tv.setTextColor(YOverlayTokens.menuSecondaryText(YOverlayUi.scopedContext(c, "yfloat")));
        return tv;
    }

    private static TextView baseText(Context c, String text) {
        TextView tv = new TextView(YOverlayUi.scopedContext(c, "yfloat"));
        tv.setText(text == null ? "" : text);
        tv.setTextColor(YOverlayTokens.menuPrimaryText(YOverlayUi.scopedContext(c, "yfloat")));
        YView.styleBody(tv);
        tv.setBackground(YOverlayUi.actionRipple(c, "yfloat"));
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setMinHeight(Math.max(YView.rowHeight(YOverlayUi.scopedContext(c, "yfloat")),
                YOverlayUi.actionHeightPx(c, "yfloat")));
        tv.setIncludeFontPadding(true);
        return tv;
    }

    static int dp(Context c, int value) { return YOverlayTokens.dp(c, value); }

    private FloatingMenuUi() {}
}
