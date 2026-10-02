package com.yagay.ysuite.standalone;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.yagay.suite.core.FeatureRegistry;
import com.yagay.suite.core.FeatureSpec;

/**
 * Generic single-feature host.
 *
 * <p>The same feature runtime used by the combined YSuite host is attached here, so standalone
 * builds do not grow a second root/logging/runtime implementation.</p>
 */
public final class StandaloneApplication extends Application {
    static final String META_FEATURE_ID = "com.yagay.ysuite.standalone.FEATURE_ID";
    static final String META_ENTRY_ACTIVITY = "com.yagay.ysuite.standalone.ENTRY_ACTIVITY";
    private static final String TAG = "YSuite.Standalone";

    @Override
    public void onCreate() {
        super.onCreate();
        String featureId = metadata().getString(META_FEATURE_ID, "");
        FeatureSpec feature = FeatureRegistry.INSTANCE.find(featureId);
        if (feature == null) {
            throw new IllegalStateException("Unknown standalone feature: " + featureId);
        }
        try {
            feature.initialize(this);
            Log.i(TAG, "Standalone host initialized feature=" + featureId);
        } catch (Throwable error) {
            Log.e(TAG, "Standalone feature runtime initialization failed: " + featureId, error);
        }
    }

    Bundle metadata() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    getPackageName(),
                    PackageManager.GET_META_DATA);
            return info.metaData != null ? info.metaData : Bundle.EMPTY;
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("Cannot read standalone host metadata", error);
        }
    }
}
