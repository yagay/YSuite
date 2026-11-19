package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Transparent, compact vertical icon + name launcher beside the active float icon.
 * No framed popup, card surface, title bar or oversized touch panel.
 */
final class ShortcutAppOverlay {
    private static FlOverlayWindowHost host;
    private static View surface;
    private static final String TAG = "app_shortcut_menu";

    static synchronized boolean isShowing() { return surface != null; }

    static synchronized void toggle(Context context, Rect iconBounds) {
        if (surface != null) {
            dismiss();
            return;
        }
        List<String> packages = ShortcutAppStore.load(context);
        if (packages.isEmpty()) {
            Toast.makeText(context, R.string.yfloat_shortcuts_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        final int iconSize = ScreenGeometry.dp(context, 26);
        final int itemGap = ScreenGeometry.dp(context, 5);
        final int inset = ScreenGeometry.dp(context, 3);
        final int rowHeight = ScreenGeometry.dp(context, 38);
        final int maxWidth = ScreenGeometry.dp(context, 198);
        Rect usable = ScreenGeometry.usableBounds(context);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundColor(Color.TRANSPARENT);
        body.setPadding(0, 0, 0, 0);

        int measuredWidth = ScreenGeometry.dp(context, 56);
        int count = 0;
        for (String pkg : packages) {
            try {
                var pm = context.getPackageManager();
                if (pm.getLaunchIntentForPackage(pkg) == null) continue;
                String name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                Drawable icon = pm.getApplicationIcon(pkg);
                icon.setBounds(0, 0, iconSize, iconSize);
                TextView row = new TextView(context);
                row.setText(name);
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                row.setTextColor(Color.WHITE);
                row.setShadowLayer(ScreenGeometry.dp(context, 2), 0, 1,
                        0xD9000000);
                row.setSingleLine(true);
                row.setEllipsize(TextUtils.TruncateAt.END);
                row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                row.setPadding(inset, 0, inset, 0);
                row.setCompoundDrawablePadding(itemGap);
                row.setCompoundDrawables(icon, null, null, null);
                row.setBackgroundColor(Color.TRANSPARENT);
                row.setContentDescription(name);
                row.setOnClickListener(v -> {
                    dismiss();
                    if (!ShortcutAppStore.launch(context, pkg)) {
                        Toast.makeText(context, R.string.yfloat_shortcuts_launch_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                body.addView(row, new LinearLayout.LayoutParams(-1, rowHeight));
                int desired = (int) Math.ceil(row.getPaint().measureText(name))
                        + iconSize + itemGap + inset * 2 + ScreenGeometry.dp(context, 2);
                measuredWidth = Math.max(measuredWidth, desired);
                count++;
            } catch (Exception e) {
                DiagnosticLog.i(context, "APP_SHORTCUTS", "skip package=" + pkg);
            }
        }
        if (count == 0) {
            Toast.makeText(context, R.string.yfloat_shortcuts_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        int maxHeight = Math.max(rowHeight,
                Math.min(ScreenGeometry.dp(context, 380), usable.height() * 2 / 3));
        int width = Math.min(maxWidth, Math.min(measuredWidth,
                Math.max(ScreenGeometry.dp(context, 80), usable.width() - ScreenGeometry.dp(context, 16))));
        int height = Math.min(count * rowHeight, maxHeight);
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(body);
        // Position relative to the entire icon's touch footprint, not its centre.
        // A centre anchor used to put the transparent list over half of the icon.
        int gap = ScreenGeometry.dp(context, 10);
        Rect icon = iconBounds == null ? new Rect() : new Rect(iconBounds);
        if (icon.isEmpty()) {
            icon.set(usable.centerX() - gap, usable.centerY() - gap,
                    usable.centerX() + gap, usable.centerY() + gap);
        }
        boolean opensRight = icon.centerX() < usable.centerX();
        int desiredX = opensRight ? icon.right + gap : icon.left - gap - width;
        int minX = usable.left + gap;
        int maxX = Math.max(minX, usable.right - width - gap);
        int x = ScreenGeometry.clamp(desiredX, minX, maxX);
        // If the preferred side is cramped, try the other side without overlap.
        if (opensRight && x < icon.right + gap && icon.left - gap - width >= minX) {
            x = icon.left - gap - width;
        } else if (!opensRight && x + width > icon.left - gap
                && icon.right + gap + width <= usable.right - gap) {
            x = icon.right + gap;
        }
        // Never create an overlay over the icon, including touch-invisible pixels.
        if (x < icon.right && x + width > icon.left) {
            DiagnosticLog.i(context, "APP_SHORTCUTS",
                    "no room beside icon rect=" + icon + " width=" + width);
            Toast.makeText(context, R.string.yfloat_shortcuts_overlay_failed,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        int y = ScreenGeometry.clamp(icon.centerY() - height / 2,
                usable.top + gap,
                Math.max(usable.top + gap, usable.bottom - height - gap));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        FlOverlayWindowHost nextHost = new FlOverlayWindowHost(context);
        if (nextHost.add(scroll, lp, TAG)) {
            host = nextHost;
            surface = scroll;
            DiagnosticLog.i(context, "APP_SHORTCUTS", "compact transparent show count="
                    + count + " width=" + width + " height=" + height
                    + " x=" + x + " y=" + y);
        } else {
            Toast.makeText(context, R.string.yfloat_shortcuts_overlay_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    static synchronized void dismiss() {
        View current = surface;
        FlOverlayWindowHost currentHost = host;
        surface = null;
        host = null;
        if (current != null && currentHost != null) currentHost.remove(current, TAG);
    }

    private ShortcutAppOverlay() {}
}
