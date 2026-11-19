package com.yagay.YFloat;

import android.os.SystemClock;
import android.view.View;

/**
 * Coalesce high-frequency pointer updates into one latest-state overlay update.
 *
 * <p>Choreographer aligns work to vsync; the 16 ms interval prevents a 120 Hz touch
 * stream from issuing 120 binder/layout transactions per second while video is
 * playing. Call cancel() before detaching a window and flush() only when the final
 * position must be committed.</p>
 */
final class OverlayFrameScheduler {
    private static final long MIN_UPDATE_INTERVAL_MS = 16L;

    private final View anchor;
    private final Runnable update;
    private final Runnable frame;
    private boolean scheduled;
    private long lastUpdateAt;

    OverlayFrameScheduler(View anchor, Runnable update) {
        this.anchor = anchor;
        this.update = update;
        this.frame = () -> {
            scheduled = false;
            lastUpdateAt = SystemClock.uptimeMillis();
            this.update.run();
        };
    }

    void request() {
        if (scheduled) return;
        long elapsed = SystemClock.uptimeMillis() - lastUpdateAt;
        long delay = lastUpdateAt == 0L ? 0L
                : Math.max(0L, MIN_UPDATE_INTERVAL_MS - elapsed);
        scheduled = true;
        anchor.postOnAnimationDelayed(frame, delay);
    }

    void cancel() {
        if (!scheduled) return;
        anchor.removeCallbacks(frame);
        scheduled = false;
    }

    void flush() {
        cancel();
        lastUpdateAt = SystemClock.uptimeMillis();
        update.run();
    }
}
