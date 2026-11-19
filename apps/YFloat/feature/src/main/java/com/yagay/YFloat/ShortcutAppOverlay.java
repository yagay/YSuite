package com.yagay.YFloat;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** A compact vertical list next to the YFloat icon, never a full-screen activity. */
final class ShortcutAppOverlay {
    private static FlOverlayWindowHost host;
    private static View surface;
    private static final String TAG = "app_shortcut_menu";

    static synchronized void toggle(Context context, int anchorX, int anchorY) {
        if (surface != null) {
            dismiss();
            return;
        }
        List<String> packages = ShortcutAppStore.load(context);
        if (packages.isEmpty()) {
            Toast.makeText(context, R.string.yfloat_shortcuts_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Rect usable = ScreenGeometry.usableBounds(context);
        int width = Math.min(ScreenGeometry.dp(context, 236),
                Math.max(ScreenGeometry.dp(context, 130), usable.width() - ScreenGeometry.dp(context, 24)));
        int rowHeight = ScreenGeometry.dp(context, 50);
        int maxHeight = Math.max(rowHeight, Math.min(
                ScreenGeometry.dp(context, 420), usable.height() * 2 / 3));
        LinearLayout body = FloatingMenuUi.root(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int count = 0;
        for (String pkg : packages) {
            try {
                var pm = context.getPackageManager();
                if (pm.getLaunchIntentForPackage(pkg) == null) continue;
                CharSequence appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                Drawable icon = pm.getApplicationIcon(pkg);
                TextView item = FloatingMenuUi.row(context, appName.toString(), icon);
                item.setSingleLine(true);
                item.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.setContentDescription(appName);
                item.setOnClickListener(v -> {
                    dismiss();
                    if (!ShortcutAppStore.launch(context, pkg)) {
                        Toast.makeText(context, R.string.yfloat_shortcuts_launch_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                body.addView(item, new LinearLayout.LayoutParams(-1, rowHeight));
                count++;
            } catch (Exception e) {
                DiagnosticLog.i(context, "APP_SHORTCUTS", "skip package=" + pkg);
            }
        }
        if (count == 0) {
            Toast.makeText(context, R.string.yfloat_shortcuts_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(count * rowHeight > maxHeight);
        scroll.addView(body);
        scroll.setOnTouchListener((v,e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                dismiss();
                return true;
            }
            return false;
        });
        int height = Math.min(count * rowHeight, maxHeight);
        int gap = ScreenGeometry.dp(context, 8);
        int left = anchorX + gap;
        if (left + width > usable.right - gap)
            left = anchorX - width - gap;
        int x = ScreenGeometry.clamp(left, usable.left + gap,
                Math.max(usable.left + gap, usable.right - width - gap));
        int y = ScreenGeometry.clamp(anchorY - height / 2,
                usable.top + gap,
                Math.max(usable.top + gap, usable.bottom - height - gap));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        FlOverlayWindowHost nextHost = new FlOverlayWindowHost(context);
        if (nextHost.add(scroll, lp, TAG)) {
            host = nextHost;
            surface = scroll;
            DiagnosticLog.i(context, "APP_SHORTCUTS", "shown count=" + count
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
