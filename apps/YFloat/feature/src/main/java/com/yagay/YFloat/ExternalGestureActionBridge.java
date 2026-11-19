package com.yagay.YFloat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** External action bridges. Other apps retain ownership of their windows and state. */
final class ExternalGestureActionBridge {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String ATLAS = "uk.wort.atlas";
    private static final String NEXTEP = "io.github.lujinxin.nextep";

    static void openSuite(Context context) {
        launch(context, "com.yagay.YSuite");
    }

    static void openAtlasPanel(Context context) {
        if (!installed(context, ATLAS)) {
            unavailable(context);
            return;
        }
        try {
            Intent open = new Intent("uk.wort.atlas.overlay.OPEN_PANEL");
            open.setClassName(ATLAS, "uk.wort.atlas.overlay.AtlasExternalActionReceiver");
            context.sendBroadcast(open);
            DiagnosticLog.i(context, "EXTERNAL_ACTION", "atlas open panel requested");
        } catch (Exception e) {
            DiagnosticLog.i(context, "EXTERNAL_ACTION", "atlas failed " + e);
            unavailable(context);
        }
    }

    static void toggleNeXtep(Context context) {
        if (!installed(context, NEXTEP)) {
            unavailable(context);
            return;
        }
        IO.execute(() -> {
            RootCommandExecutor.Result state =
                    RootCommandExecutor.runText("settings get global nextep_workspace_active_v1", 3, 1024);
            if (!state.success()) {
                failed(context, "NeXtep state", state);
                return;
            }
            String current = state.text();
            if (!"0".equals(current) && !"1".equals(current) && !"null".equals(current)) {
                DiagnosticLog.i(context, "EXTERNAL_ACTION", "NeXtep unexpected state=" + current);
                unavailable(context);
                return;
            }
            String request = "1".equals(current) ? "false" : "true";
            String command = "am broadcast -n "
                    + "io.github.lujinxin.nextep/.trigger.WorkspaceControlBrokerReceiver"
                    + " -a io.github.lujinxin.nextep.action.HOST_SET_REQUEST"
                    + " --ez io.github.lujinxin.nextep.extra.REQUESTED_ACTIVE " + request;
            RootCommandExecutor.Result result = RootCommandExecutor.runText(command, 5, 2048);
            if (!result.success()) {
                failed(context, "NeXtep toggle", result);
            } else {
                DiagnosticLog.i(context, "EXTERNAL_ACTION", "NeXtep requestedActive=" + request);
            }
        });
    }

    /**
     * MiCTS owns the actual Circle to Search trigger: its exported translucent
     * launcher invokes VoiceInteractionManagerService.showSessionFromSession().
     * A floating Service cannot reliably launch activities on Android 16 due to
     * background-activity-launch restrictions. Prefer bounded Root "am start".
     */
    static void googleCircle(Context context) {
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            // Do not call resolveActivity(): Android package visibility could hide
            // the exported MiCTS launcher even when MiCTS is installed.
            final String component =
                    "com.parallelc.micts/com.parallelc.micts.ui.activity.MainActivity";
            RootCommandExecutor.Result result = RootCommandExecutor.runText(
                    "am start -n " + component, 6, 4096);
            if (result.success()
                    && !result.text().contains("Error:")
                    && !result.text().contains("Exception")) {
                DiagnosticLog.i(app, "EXTERNAL_ACTION",
                        "MiCTS started through root activity manager");
                return;
            }
            DiagnosticLog.i(app, "EXTERNAL_ACTION",
                    "MiCTS root launch failed: " + result.failureMessage("timeout")
                            + " output=" + result.text());
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.parallelc.micts",
                            "com.parallelc.micts.ui.activity.MainActivity");
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    app.startActivity(intent);
                    DiagnosticLog.i(app, "EXTERNAL_ACTION",
                            "MiCTS direct launcher requested");
                } catch (Exception e) {
                    DiagnosticLog.i(app, "EXTERNAL_ACTION",
                            "MiCTS direct launcher failed=" + e);
                    unavailable(app);
                }
            });
        });
    }

    private static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static void launch(Context c, String pkg) {
        try {
            Intent intent = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) { unavailable(c); return; }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
        } catch (Exception e) {
            DiagnosticLog.i(c, "EXTERNAL_ACTION", "launch failed pkg=" + pkg + " " + e);
            unavailable(c);
        }
    }

    private static void failed(Context context, String action, RootCommandExecutor.Result result) {
        DiagnosticLog.i(context, "EXTERNAL_ACTION", action + " failed="
                + result.failureMessage("timeout"));
        unavailable(context);
    }

    private static void unavailable(Context context) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> Toast.makeText(context.getApplicationContext(),
                        R.string.yfloat_external_action_unavailable, Toast.LENGTH_SHORT).show());
    }

    private ExternalGestureActionBridge() {}
}
