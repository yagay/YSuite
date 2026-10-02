package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Root screencap helper. Never blocks the main thread and never waits forever for su. */
public final class RootCapture {
    private static final long ROOT_CAPTURE_TIMEOUT_SECONDS = 8L;
    private static final int MAX_CAPTURE_BYTES = 64 * 1024 * 1024;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "YFloat-root-capture");
        t.setDaemon(true);
        return t;
    });

    static void captureAsync(Context c, Consumer<Bitmap> ok, Consumer<Throwable> fail) {
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            try {
                RootCommandExecutor.Result command = RootCommandExecutor.runBinary(
                        "screencap -p 2>/dev/null", ROOT_CAPTURE_TIMEOUT_SECONDS, MAX_CAPTURE_BYTES);
                if (!command.success()) {
                    throw new IllegalStateException(
                            command.failureMessage(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_a6941ff829ca)), command.error);
                }
                byte[] data = command.stdout;
                if (data.length < 1024) throw new IllegalStateException(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_dac2c06f81a6));
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bitmap == null) throw new IllegalStateException(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_a2c49ab067d1));
                app.getMainExecutor().execute(() -> ok.accept(bitmap));
            } catch (Throwable t) {
                app.getMainExecutor().execute(() -> fail.accept(t));
            }
        });
    }

    private RootCapture() {}
}
