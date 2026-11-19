package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Rect;

/** Single entry point for every YFloat text action menu. */
final class TextActionMenuController {
    private final Context context;

    TextActionMenuController(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    void show(SelectionSnapshot snapshot, Runnable selectAll) {
        if (context == null || snapshot == null || snapshot.text().isBlank()) return;
        Rect anchor = snapshot.screenBounds();
        if (anchor == null || anchor.isEmpty()) {
            // Without valid physical-screen geometry a missing anchor would
            // place the menu at a fixed default position unrelated to text.
            dismiss();
            return;
        }
        FloatActionMenu.showTextAt(context, snapshot.text(), selectAll, anchor);
    }

    void dismiss() {
        FloatActionMenu.dismiss();
    }
}
