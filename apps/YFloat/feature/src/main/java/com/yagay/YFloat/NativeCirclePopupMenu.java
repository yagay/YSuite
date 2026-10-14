package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Real Android View-based floating contextual popup for Native Circle.
 *
 * This is a separate touchable window, not Canvas-drawn buttons. YUI owns its
 * background, ripple, text colors, density and elevation. One popup is reused
 * and repositioned with selection changes; no Google app is involved.
 */
final class NativeCirclePopupMenu {
    private static final int MORE = -1;
    private static final int BACK = -2;
    private final Context app;
    private final View anchor;
    private final FlOverlayWindowHost host;
    private final LinearLayout root;
    private final LinearLayout row;
    private final WindowManager.LayoutParams lp;
    private final int dp;
    private final int[] screenOrigin = new int[2];

    private boolean attached;
    private boolean expanded;
    private boolean imageMode;
    private boolean hasMode;
    private RectF selected = new RectF();
    private IntConsumer command;
    private int width;

    NativeCirclePopupMenu(Context context, View anchor) {
        app = context.getApplicationContext();
        this.anchor = anchor;
        host = new FlOverlayWindowHost(app);
        dp = Math.max(1, Math.round(app.getResources().getDisplayMetrics().density));
        root = FloatingMenuUi.root(app);
        root.setPadding(dp * 3, dp * 3, dp * 3, dp * 3);
        row = new LinearLayout(app);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
    }

    void show(RectF bounds, boolean isImage, IntConsumer onCommand) {
        if (bounds == null || bounds.isEmpty() || anchor.getWidth() <= 0
                || anchor.getHeight() <= 0 || onCommand == null) {
            dismiss();
            return;
        }
        selected = new RectF(bounds);
        command = onCommand;
        if (!hasMode || imageMode != isImage) {
            expanded = false;
            imageMode = isImage;
            hasMode = true;
            buildRow();
        } else if (row.getChildCount() == 0) {
            buildRow();
        }
        position();
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        root.setVisibility(View.VISIBLE);
        if (attached) {
            if (!host.update(root, lp, "native_circle_popup_update")) {
                root.setVisibility(View.INVISIBLE);
            }
            return;
        }
        attached = host.addStable(root, lp, "native_circle_popup");
        if (!attached) {
            DiagnosticLog.i(app, "CIRCLE_NATIVE_POPUP",
                    "failed to attach floating action menu");
        }
    }

    /** Hide without detaching: Android's overlay window stays owned by the
     * workspace and cannot race the asynchronous WindowManager detach.
     */
    void dismiss() {
        if (attached) {
            root.setVisibility(View.INVISIBLE);
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            host.update(root, lp, "native_circle_popup_hide");
        }
        hasMode = false;
        expanded = false;
        command = null;
    }

    void destroy() {
        dismiss();
        if (attached) {
            attached = false;
            host.remove(root, "native_circle_popup_close");
        }
    }

    private String[] labels() {
        if (imageMode) {
            return new String[]{label("识图", "Find"),
                    label("截图", "Crop"), label("复制", "Copy"),
                    label("分享", "Share"), label("重选", "Reset")};
        }
        return new String[]{label("复制", "Copy"),
                label("翻译", "Translate"), label("搜索", "Search"),
                label("分享", "Share"), label("重选", "Reset")};
    }

    private String label(String zh, String en) {
        return Locale.getDefault().getLanguage().equals("zh") ? zh : en;
    }

    private void buildRow() {
        row.removeAllViews();
        String[] all = labels();
        int visible = primaryCount();
        if (!expanded) {
            for (int i = 0; i < visible; i++) addButton(all[i], i);
            if (visible < all.length) addButton(label("更多 ›", "More ›"), MORE);
        } else {
            addButton(label("‹ 返回", "‹ Back"), BACK);
            for (int i = visible; i < all.length; i++) addButton(all[i], i);
        }
        row.measure(View.MeasureSpec.makeMeasureSpec(anchor.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(anchor.getHeight(), View.MeasureSpec.AT_MOST));
        width = Math.min(anchor.getWidth() - dp * 16,
                Math.max(dp * 84, row.getMeasuredWidth() + dp * 6));
        lp.width = Math.max(dp * 84, width);
    }

    private int primaryCount() {
        // The system text selection toolbar shows a compact first row,
        // with less frequent actions under More on both narrow/wide devices.
        return anchor.getWidth() < dp * 310 ? 2 : 3;
    }

    private void addButton(String title, int index) {
        TextView action = FloatingMenuUi.action(app, title);
        action.setSingleLine(true);
        action.setEllipsize(TextUtils.TruncateAt.END);
        action.setMaxLines(1);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription(title);
        action.setOnClickListener(view -> {
            if (index == MORE || index == BACK) {
                expanded = index == MORE;
                buildRow();
                position();
                if (attached) host.update(root, lp, "native_circle_popup_more");
                return;
            }
            IntConsumer selectedCommand = command;
            dismiss();
            if (selectedCommand != null) selectedCommand.accept(index);
        });
        row.addView(action, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void position() {
        anchor.getLocationOnScreen(screenOrigin);
        RectF selection = selected;
        int menuHeight = Math.max(dp * 48, row.getMeasuredHeight() + dp * 6);
        float localX = Math.max(dp * 8, Math.min(
                (selection.left + selection.right - lp.width) / 2f,
                anchor.getWidth() - lp.width - dp * 8));
        float above = selection.top - menuHeight - dp * 10;
        float below = selection.bottom + dp * 10;
        float localY = above >= dp * 62 ? above : below;
        localY = Math.max(dp * 62,
                Math.min(localY, anchor.getHeight() - menuHeight - dp * 10));

        Rect display = ScreenGeometry.displayBounds(app);
        lp.x = Math.round(screenOrigin[0] + localX - display.left);
        lp.y = Math.round(screenOrigin[1] + localY - display.top);
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
    }
}
