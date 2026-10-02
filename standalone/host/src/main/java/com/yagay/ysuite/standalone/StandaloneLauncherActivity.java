package com.yagay.ysuite.standalone;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Thin launcher that forwards directly to the selected feature's real entry activity. */
public final class StandaloneLauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle metadata = ((StandaloneApplication) getApplication()).metadata();
        String entry = metadata.getString(StandaloneApplication.META_ENTRY_ACTIVITY, "");
        if (entry.isBlank()) {
            Toast.makeText(this, "Feature entry activity is not configured", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent target = new Intent(getIntent());
        target.setComponent(new ComponentName(getPackageName(), entry));
        startActivity(target);
        finish();
    }
}
