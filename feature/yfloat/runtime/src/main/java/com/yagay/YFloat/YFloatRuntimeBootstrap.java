package com.yagay.YFloat;

import android.app.Application;
import android.content.Context;

import com.yagay.suite.api.YLocale;

public final class YFloatRuntimeBootstrap {
    private static boolean initialized;
    private static YFloatApp callbacks;

    public static synchronized void initialize(Context context) {
        if (initialized || context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        YLocale.initialize(app);
        try { SettingsMigrator.run(app); } catch (Throwable ignored) {}
        try { RemovedFeatureMigration.run(app); } catch (Throwable ignored) {}
        try { HookReloadManager.initialize(app); } catch (Throwable ignored) {}
        try { YFloatHostIdentity.initialize(app); } catch (Throwable ignored) {}
        try { LsposedStatusManager.initialize(app); } catch (Throwable ignored) {}
        if (app instanceof Application) {
            callbacks = new YFloatApp();
            ((Application) app).registerActivityLifecycleCallbacks(callbacks);
        }
        initialized = true;
    }

    private YFloatRuntimeBootstrap() {}
}
