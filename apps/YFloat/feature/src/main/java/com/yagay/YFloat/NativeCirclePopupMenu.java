package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Image-crop actions for Native Circle. Text selection uses the existing
 * FloatActionMenu, rather than maintaining a second text-menu implementation.
 *
 * This adapter uses the same FloatingMenuUi / FloatingMenuPositioner as the
 * rest of YFloat. Buttons retain YUI intrinsic metrics and the window is reused
 * when a selection is adjusted.
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
    private final int marginPx;
    private final int[] screenOrigin = new int[2];

    private boolean attached;
    private boolean expanded;
    private boolean configured;
    private final RectF selected = new RectF();
    private IntConsumer command;

    NativeCirclePopupMenu(Context context, View anchor) {
        app = context.getApplicationContext();
        this.anchor = anchor;
        host = new FlOverlayWindowHost(app);
        marginPx = com.yagay.yui.YOverlayUi.contentPaddingPx(app, "yfloat");

        root = FloatingMenuUi.root(app);
        root.setPadding(marginPx, marginPx, marginPx, marginPx);
        HorizontalScrollView scroller = new HorizontalScrollView(app);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        row = new LinearLayout(app);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroller.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
    }

    void show(RectF bounds, IntConsumer onCommand) {
        if (bounds == null || bounds.isEmpty() || anchor.getWidth() <= 0
                || anchor.getHeight() <= 0 || onCommand == null) {
            dismiss();
            return;
        }
        selected.set(bounds);
        command = onCommand;
        if (!configured) {
            expanded = false;
            configured = true;
            buildRow();
        }
        position();
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        root.setVisibility(View.VISIBLE);
        if (attached && host.update(root, lp, "native_circle_popup_update")) return;

        if (attached) {
            DiagnosticLog.i(app, "CIRCLE_NATIVE_POPUP",
                    "update failed; reattaching image action menu");
            host.remove(root, "native_circle_popup_recover");
            attached = false;
        }
        if (root.isAttachedToWindow()) {
            // A failed removal must not create a duplicate overlay window.
            root.setVisibility(View.INVISIBLE);
            DiagnosticLog.i(app, "CIRCLE_NATIVE_POPUP",
                    "reattach deferred because prior window is still attached");
            return;
        }
        attached = host.addStable(root, lp, "native_circle_popup");
        if (!attached) {
            root.setVisibility(View.INVISIBLE);
            DiagnosticLog.i(app, "CIRCLE_NATIVE_POPUP",
                    "unable to attach image action menu on either host");
        }
    }

    void dismiss() {
        if (attached) {
            root.setVisibility(View.INVISIBLE);
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (!host.update(root, lp, "native_circle_popup_hide")) {
                host.remove(root, "native_circle_popup_hide_failed");
                attached = false;
            }
        }
        configured = false;
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

    // Index contract: 0 Finish, 1 Find, 2 Copy, 3 Share, 4 Save, 5 Reset.
    private String[] labels() {
        return new String[]{label("完成", "Done"),
                label("识图", "Find"), label("复制", "Copy"),
                label("分享", "Share"), label("保存", "Save"),
                label("重选", "Reset")};
    }

    private String label(String zh, String en) {
        return Locale.getDefault().getLanguage().equals("zh") ? zh : en;
    }

    private void buildRow() {
        row.removeAllViews();
        String[] actions = labels();
        int primary = Math.min(2, actions.length);
        if (!expanded) {
            for (int i = 0; i < primary; i++) addButton(actions[i], i);
            if (primary < actions.length) addButton(label("更多 ›", "More ›"), MORE);
        } else {
            addButton(label("‹ 返回", "‹ Back"), BACK);
            for (int i = primary; i < actions.length; i++) addButton(actions[i], i);
        }
        int maxWidth = Math.max(1, ScreenGeometry.usableBounds(app).width()
                - marginPx * 2);
        root.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(anchor.getHeight(),
                        View.MeasureSpec.AT_MOST));
        lp.width = Math.max(1, Math.min(maxWidth, root.getMeasuredWidth()));
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
    }

    private void addButton(String title, int index) {
        TextView button = FloatingMenuUi.action(app, title);
        button.setContentDescription(title);
        button.setOnClickListener(view -> {
            if (index == MORE || index == BACK) {
                expanded = index == MORE;
                buildRow();
                position();
                if (attached && !host.update(root, lp, "native_circle_popup_more")) {
                    show(selected, command);
                }
                return;
            }
            IntConsumer callback = command;
            dismiss();
            if (callback != null) callback.accept(index);
        });
        row.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void position() {
        anchor.getLocationOnScreen(screenOrigin);
        Rect box = new Rect(
                Math.round(screenOrigin[0] + selected.left),
                Math.round(screenOrigin[1] + selected.top),
                Math.round(screenOrigin[0] + selected.right),
                Math.round(screenOrigin[1] + selected.bottom));
        int height = Math.max(root.getMeasuredHeight(),
                com.yagay.yui.YOverlayUi.actionHeightPx(app, "yfloat"));
        int[] xy = FloatingMenuPositioner.aroundAnchor(
                app, ScreenGeometry.usableBounds(app), box, lp.width, height, false);
        Rect display = ScreenGeometry.displayBounds(app);
        lp.x = xy[0] - display.left;
        lp.y = xy[1] - display.top;
    }
}
