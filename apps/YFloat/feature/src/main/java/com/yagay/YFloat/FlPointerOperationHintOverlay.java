package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Color;
import com.yagay.yui.YOverlayUi;
import com.yagay.yui.YOverlayTokens;
import com.yagay.yui.YView;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** YFloat pointer operation hint; uses FL selection geometry. */
public final class FlPointerOperationHintOverlay {
    public enum Mode { TEXT, IMAGE, SCREENSHOT }

    private static final float PROBE_X_OFFSET_DP = 15f; // Physical probe geometry, not UI styling.

    private final Context context;
    private final FlOverlayWindowHost windowHost;
    private final int hintHeightPx;
    private final int probeXOffsetPx;
    private final int iconSizePx;
    private final LinearLayout root;
    private final ImageView icon;
    private final TextView text;
    private final WindowManager.LayoutParams lp;
    private boolean attached;
    private boolean contentVisible;
    private Mode mode;

    public FlPointerOperationHintOverlay(Context c) {
        context = c.getApplicationContext();
        windowHost = new FlOverlayWindowHost(context);
        iconSizePx = YView.iconSize(YOverlayUi.scopedContext(context, "yfloat"));
        hintHeightPx = Math.max(iconSizePx,
                YOverlayUi.actionHeightPx(context, "yfloat") / 2);
        probeXOffsetPx = dp(PROBE_X_OFFSET_DP);

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);

        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int innerPad = Math.max(1, Math.round(iconSizePx / 6f));
        icon.setPadding(innerPad, innerPad, innerPad, innerPad);
        icon.setVisibility(View.GONE);
        root.addView(icon, new LinearLayout.LayoutParams(iconSizePx, iconSizePx));

        text = new TextView(YOverlayUi.scopedContext(context, "yfloat"));
        YOverlayUi.styleHintText(text, "yfloat");
        text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        text.setMaxWidth(Math.max(iconSizePx * 3,
                ScreenGeometry.usableBounds(context).width() / 3));
        text.setMaxLines(1);
        text.setVisibility(View.GONE);
        root.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                hintHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = -hintHeightPx;
        lp.y = 0;
    }

    public void syncToProbeWindow(int probeWindowX, int probeWindowY) {
        int nextX = probeWindowX + probeXOffsetPx;
        int nextY = probeWindowY - hintHeightPx;
        boolean changed = lp.x != nextX || lp.y != nextY;
        lp.x = nextX;
        lp.y = nextY;

        if (!attached) {
            attached = windowHost.add(root, lp, "pointer_hint");
            if (attached) {
                DiagnosticLog.i(context, "FL_POINTER_HINT",
                        "ATTACH pos=" + lp.x + "," + lp.y
                                + " height=" + hintHeightPx + " xOffset=" + probeXOffsetPx
                                + " accessibilityHost=" + windowHost.isAccessibilityHosted());
            }
            return;
        }
        if (changed) windowHost.update(root, lp, "pointer_hint");
    }

    public void show(Mode next, int probeWindowX, int probeWindowY) {
        show(next, probeWindowX, probeWindowY, null);
    }

    public void show(Mode next, int probeWindowX, int probeWindowY, String optionalText) {
        if (next == null) next = Mode.SCREENSHOT;
        syncToProbeWindow(probeWindowX, probeWindowY);
        if (!attached) return;

        if (mode != next) {
            mode = next;
            int drawableRes;
            int background;
            switch (next) {
                case TEXT -> { drawableRes = R.drawable.fl_pointer_text; background = YOverlayTokens.warning(YOverlayUi.scopedContext(context, "yfloat")); }
                case IMAGE -> { drawableRes = R.drawable.fl_pointer_image; background = YOverlayTokens.accent(YOverlayUi.scopedContext(context, "yfloat")); }
                case SCREENSHOT -> { drawableRes = R.drawable.fl_pointer_screenshot; background = YOverlayTokens.warning(YOverlayUi.scopedContext(context, "yfloat")); }
                default -> { drawableRes = R.drawable.fl_pointer_screenshot; background = YOverlayTokens.warning(YOverlayUi.scopedContext(context, "yfloat")); }
            }
            icon.setImageResource(drawableRes);
            icon.setBackground(makeCircle(background));
        }

        icon.setVisibility(View.VISIBLE);
        if (optionalText == null) {
            text.setVisibility(View.GONE);
        } else {
            text.setText(optionalText);
            text.setVisibility(View.VISIBLE);
        }
        contentVisible = true;
        root.requestLayout();
        windowHost.update(root, lp, "pointer_hint");
        DiagnosticLog.i(context, "FL_POINTER_HINT",
                "SHOW mode=" + mode + " pos=" + lp.x + "," + lp.y
                        + " icon=" + iconSizePx + "x" + iconSizePx
                        + " text=" + (optionalText == null ? "none" : optionalText));
    }

    public void hideContent() {
        if (!attached) return;
        if (!contentVisible && icon.getVisibility() == View.GONE && text.getVisibility() == View.GONE) return;
        contentVisible = false;
        icon.setVisibility(View.GONE);
        text.setVisibility(View.GONE);
        root.requestLayout();
        windowHost.update(root, lp, "pointer_hint");
        DiagnosticLog.i(context, "FL_POINTER_HINT", "CONTENT_HIDE pos=" + lp.x + "," + lp.y);
    }

    public void close() {
        contentVisible = false;
        if (attached) windowHost.remove(root, "pointer_hint");
        attached = false;
        mode = null;
    }

    public boolean isAttached() { return attached; }

    private GradientDrawable makeCircle(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        return bg;
    }

    private int dp(float value) {
        float density = Math.max(.1f, context.getResources().getDisplayMetrics().density);
        return Math.max(1, Math.round(value * density));
    }
}
